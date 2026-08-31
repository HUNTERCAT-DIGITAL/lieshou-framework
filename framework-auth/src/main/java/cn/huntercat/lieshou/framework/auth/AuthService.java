package cn.huntercat.lieshou.framework.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginWithCodeRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.RefreshRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.RegisterRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.ResetPasswordRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.ActivateRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.SendCodeRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.TokenResponse;
import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import cn.huntercat.lieshou.framework.common.dto.UserAuthView;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;

/**
 * Auth 业务服务: login + refresh + me.
 *
 * <p>不持有 user 表；通过 Feign 调 user-service 验证.
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /** 依赖故障统一文案（端口抛异常 = 上游不可用；返回 null = 业务否定，两者绝不混用） */
  private static final String UPSTREAM_UNAVAILABLE = "用户服务暂不可用，请稍后重试";

  private final JwtService jwt;
  private final UserAuthPort userClient;
  private final PasswordEncoder passwordEncoder;

  /** 默认租户编码（未传 tenantCode 时） · 全局统一 default（2026-08-29 决策：无特别说明的默认租户均为 default） · 可配置（auth.default-tenant-code） */
  private final String defaultTenantCode;

  public AuthService(
      JwtService jwt,
      UserAuthPort userClient,
      PasswordEncoder passwordEncoder,
      @Value("${auth.default-tenant-code:default}") String defaultTenantCode) {
    this.jwt = jwt;
    this.userClient = userClient;
    this.passwordEncoder = passwordEncoder;
    this.defaultTenantCode = defaultTenantCode;
  }

  /**
   * 登录: tenantCode + username + password → access + refresh tokens.
   *
   * <p>Phase 6: 校验账户 status（非 ACTIVE 拒绝登录）；登录成功回写 last_login_at.
   *
   * <p>Phase 8（ADR-0022）: 按租户鉴权，JWT 带 tid/tcode.
   *
   * @throws UsernameNotFoundException 用户不存在
   * @throws BadCredentialsException 密码错误或账户被禁用/锁定
   */
  public TokenResponse login(LoginRequest req) {
    String tenantCode =
        (req.tenantCode() == null || req.tenantCode().isBlank())
            ? defaultTenantCode
            : req.tenantCode();
    UserAuthView user;
    try {
      user = userClient.findByTenantAndUsername(tenantCode, req.username());
    } catch (RuntimeException e) {
      // 端口抛异常 = user-service 不可达/故障，不是"用户不存在"——必须与业务否定区分
      log.warn("login: user-service 查询失败 tenant={} username={}", tenantCode, req.username(), e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
    if (user == null || user.passwordHash() == null) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + req.username());
    }
    if (!passwordEncoder.matches(req.password(), user.passwordHash())) {
      throw new BadCredentialsException("INVALID_CREDENTIALS");
    }
    // Phase 6: 账户状态校验（null 兜底 ACTIVE，兼容旧 user-service）
    String status = user.status() == null ? "ACTIVE" : user.status();
    if (!"ACTIVE".equals(status)) {
      throw new BadCredentialsException("ACCOUNT_" + status);
    }
    List<String> roles =
        user.roles() == null || user.roles().isEmpty() ? List.of("USER") : user.roles();
    String access =
        jwt.generateAccessToken(user.id(), user.tenantId(), tenantCode, user.username(), roles);
    String refresh = jwt.generateRefreshToken(user.id(), user.username());
    markLastLogin(user.id());
    return new TokenResponse(
        access,
        refresh,
        jwt.getAccessTtlSeconds(),
        "Bearer",
        user.id(),
        user.username(),
        tenantCode,
        user.tenantName(),
        user.tenantEdition(),
        user.passwordHash() == null || user.passwordHash().isBlank(),
        tenantOptions(user.username()));
  }

  /** 登录成功回写 last_login_at（失败静默降级 + debug 日志，不影响登录主流程）. */
  private void markLastLogin(Long userId) {
    try {
      userClient.markLastLogin(userId);
    } catch (RuntimeException e) {
      log.debug("markLastLogin 回写失败（忽略） userId={}", userId, e);
    }
  }

  // ============================================================
  // Phase 8 · 认证体系扩展（ADR-0023）：验证码登录 / 注册 / 重置密码
  // ============================================================

  /** 发送验证码（短信/邮箱）。业务否定（BaseException）透传错误码；依赖故障 → 503。 */
  public void sendCode(SendCodeRequest req) {
    try {
      userClient.sendVerificationCode(
          Map.of("channel", req.channel(), "target", req.target(), "purpose", req.purpose()));
    } catch (BaseException e) {
      // 业务否定（发送太频繁/参数非法）→ 直接透传（GlobalExceptionHandler 按 errorCode + httpStatus 处理，
      // 如 SEND_TOO_FREQUENT → 400），而非转 BadCredentialsException（会被误报成 401 INVALID_CREDENTIALS）
      throw e;
    } catch (RuntimeException e) {
      log.warn(
          "sendCode 失败 channel={} target={} purpose={}",
          req.channel(),
          req.target(),
          req.purpose(),
          e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
  }

  /** 验证码登录：校验 code → 按 phone/email 查用户 → JWT */
  public TokenResponse loginWithCode(LoginWithCodeRequest req) {
    verifyCode(req.channel(), req.target(), "LOGIN", req.code());
    UserAuthView user = findUserByTarget(req.tenantCode(), req.channel(), req.target());
    if (user == null || user.id() == null) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + req.target());
    }
    return issueTokens(user, req.tenantCode());
  }

  /** 注册（验证码可选）→ 创建用户 → 注册即登录 */
  public TokenResponse register(RegisterRequest req) {
    // 简化注册：code 为空时跳过验证码校验（开放注册 · 2026-08）；非空仍校验（兼容验证码流程）
    if (req.code() != null && !req.code().isBlank()) {
      verifyCode(req.channel(), req.target(), "REGISTER", req.code());
    }
    Map<String, String> createBody = new java.util.HashMap<>();
    createBody.put("username", req.username());
    createBody.put("displayName", req.displayName());
    createBody.put("password", req.password());
    if (req.inviteCode() != null && !req.inviteCode().isBlank()) {
      // 邀请注册：租户/角色由 user-service 按邀请码解析（ADR-0023 P2）
      createBody.put("inviteCode", req.inviteCode());
    } else {
      createBody.put("tenantCode", req.tenantCode() == null ? defaultTenantCode : req.tenantCode());
    }
    if ("SMS".equals(req.channel())) {
      createBody.put("phone", req.target());
    } else {
      createBody.put("email", req.target());
    }
    Map<String, Object> created;
    try {
      created = userClient.createUser(createBody);
    } catch (BaseException e) {
      // 业务否定（用户名占用/密码弱等）→ 透传错误码
      throw new BadCredentialsException(e.errorCode(), e);
    } catch (IllegalArgumentException e) {
      // 兼容未使用 BaseException 的端口实现：透传消息
      throw new BadCredentialsException(
          e.getMessage() == null ? "REGISTER_FAILED" : e.getMessage(), e);
    } catch (RuntimeException e) {
      log.warn("register: user-service 创建用户失败 username={}", req.username(), e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
    Number uid = (Number) created.get("id");
    Number tid = (Number) created.get("tenantId");
    String tcode = (String) created.getOrDefault("tenantCode", defaultTenantCode);
    String tname = (String) created.get("tenantName");
    String tedition = (String) created.getOrDefault("tenantEdition", "GENERIC");
    return new TokenResponse(
        jwt.generateAccessToken(
            uid.longValue(),
            tid == null ? 0L : tid.longValue(),
            tcode,
            req.username(),
            List.of("USER")),
        jwt.generateRefreshToken(uid.longValue(), req.username()),
        jwt.getAccessTtlSeconds(),
        "Bearer",
        uid.longValue(),
        req.username(),
        tcode,
        tname,
        tedition,
        false, // register 自带密码,无需激活
        tenantOptions(req.username()));
  }

  /**
   * 首次登录激活（2026-08）：管理员建用户未设密码 → 登录后直接设置密码（token 即身份,无需再验证码）。
   * 从 JWT claims 取用户 → 设密码 → 重新查完整用户 → 签发 tokens（activationRequired=false）。
   */
  public TokenResponse activate(Claims claims, ActivateRequest req) {
    Long userId = claims.get("uid", Long.class);
    String tenantCode = claims.get("tcode", String.class);
    String username = claims.getSubject();
    if (userId == null || username == null) {
      throw new BadCredentialsException("INVALID_TOKEN");
    }
    try {
      userClient.updateUserPassword(userId, Map.of("password", req.password()));
    } catch (BaseException e) {
      throw new BadCredentialsException(e.errorCode(), e);
    } catch (RuntimeException e) {
      log.warn("activate: user-service 更新密码失败 userId={}", userId, e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
    // 重新查完整用户(含租户名/版别) → 签发 tokens
    UserAuthView user = userClient.findByTenantAndUsername(tenantCode, username);
    if (user == null || user.id() == null) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + username);
    }
    return issueTokens(user, tenantCode);
  }

  /** 忘记密码：校验 code → 按 phone/email 查用户 → 改密 */
  public void resetPassword(ResetPasswordRequest req) {
    verifyCode(req.channel(), req.target(), "RESET_PASSWORD", req.code());
    UserAuthView user = findUserByTarget(req.tenantCode(), req.channel(), req.target());
    if (user == null || user.id() == null) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + req.target());
    }
    try {
      userClient.updateUserPassword(user.id(), Map.of("password", req.newPassword()));
    } catch (BaseException e) {
      throw new BadCredentialsException(e.errorCode(), e);
    } catch (RuntimeException e) {
      log.warn("resetPassword: user-service 更新密码失败 userId={}", user.id(), e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
  }

  /** 校验验证码。业务否定（BaseException，如 INVALID_CODE）→ 透传错误码；依赖故障 → 503。 */
  private void verifyCode(String channel, String target, String purpose, String code) {
    try {
      userClient.verifyVerificationCode(
          Map.of("channel", channel, "target", target, "purpose", purpose, "code", code));
    } catch (BaseException e) {
      throw new BadCredentialsException(e.errorCode(), e);
    } catch (RuntimeException e) {
      log.warn("verifyCode 失败 channel={} target={} purpose={}", channel, target, purpose, e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
  }

  /**
   * 按渠道查用户（SMS→phone / EMAIL→email）。
   *
   * <p>端口返回 null = 用户不存在（业务否定）；抛异常 = user-service 故障 → 503，不吞成 null。
   */
  private UserAuthView findUserByTarget(String tenantCode, String channel, String target) {
    try {
      if ("SMS".equals(channel)) {
        return userClient.findByTenantAndPhone(tenantCode, target);
      }
      return userClient.findByEmail(target);
    } catch (RuntimeException e) {
      log.warn("findUserByTarget 查询失败 channel={} target={}", channel, target, e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
  }

  /** 签发 access + refresh（含租户维度） */
  private TokenResponse issueTokens(UserAuthView user, String tenantCode) {
    String tcode =
        (tenantCode == null || tenantCode.isBlank())
            ? (user.tenantCode() == null ? defaultTenantCode : user.tenantCode())
            : tenantCode;
    List<String> roles =
        user.roles() == null || user.roles().isEmpty() ? List.of("USER") : user.roles();
    String access =
        jwt.generateAccessToken(user.id(), user.tenantId(), tcode, user.username(), roles);
    String refresh = jwt.generateRefreshToken(user.id(), user.username());
    markLastLogin(user.id());
    boolean activationRequired =
        user.passwordHash() == null || user.passwordHash().isBlank();
    return new TokenResponse(
        access,
        refresh,
        jwt.getAccessTtlSeconds(),
        "Bearer",
        user.id(),
        user.username(),
        tcode,
        user.tenantName(),
        user.tenantEdition(),
        activationRequired,
        tenantOptions(user.username()));
  }

  /**
   * 刷新: refresh token → 新 access token.
   *
   * <p>Phase 5 简化：不做服务端黑名单（access 过期前有效; refresh 默认 7 天）; Phase 2+ 加 Redis 黑名单.
   *
   * @throws BadCredentialsException refresh token 无效 / 过期 / 类型错
   */
  public TokenResponse refresh(RefreshRequest req) {
    if (!jwt.validate(req.refreshToken())) {
      throw new BadCredentialsException("INVALID_REFRESH_TOKEN");
    }
    Claims c = jwt.parse(req.refreshToken());
    if (!"refresh".equals(c.get("typ"))) {
      throw new BadCredentialsException("WRONG_TOKEN_TYPE");
    }
    Long userId = c.get("uid", Long.class);
    Long tenantId = c.get("tid", Long.class);
    String tenantCode = c.get("tcode", String.class);
    String username = c.getSubject();
    @SuppressWarnings("unchecked")
    List<String> roles = c.get("roles", List.class);
    if (roles == null) roles = List.of("USER");
    String access = jwt.generateAccessToken(userId, tenantId, tenantCode, username, roles);
    // refresh 保持纯 JWT 校验：tenantName/tenantEdition 未知，置 null（前端刷新不覆盖租户信息）
    return new TokenResponse(
        access,
        req.refreshToken(),
        jwt.getAccessTtlSeconds(),
        "Bearer",
        userId,
        username,
        tenantCode,
        null,
        null,
        false, // switchTenant 是已登录用户,无需激活
        List.of());
  }

  /** 给 AuthController.me 用：从已验证的 JWT Claims 提取用户信息. */
  public Map<String, Object> viewFromClaims(Claims claims) {
    return Map.of(
        "userId", claims.get("uid", Long.class),
        "tenantId", claims.get("tid", Long.class),
        "tenantCode", claims.get("tcode", String.class),
        "username", claims.getSubject(),
        "roles", claims.get("roles", List.class));
  }

  /** 按用户名查可登录租户选项（多租户登录前 · tenant-options 端点）。故障降级空列表 + warn 日志。 */
  public java.util.List<java.util.Map<String, Object>> tenantOptions(String username) {
    try {
      return userClient.tenantOptions(username);
    } catch (RuntimeException e) {
      // 服务不可达/查询失败 → 空列表，前端回退默认租户登录（降级设计，记日志便于观察）
      log.warn("tenantOptions 查询失败，回退空列表 username={}", username, e);
      return java.util.List.of();
    }
  }

  /** 切换租户：refresh token + 目标租户编码 → 新双 token（先登录后选租户） */
  public TokenResponse switchTenant(String refreshToken, String tenantCode) {
    if (!jwt.validate(refreshToken)) {
      throw new BadCredentialsException("INVALID_REFRESH_TOKEN");
    }
    Claims c = jwt.parse(refreshToken);
    if (!"refresh".equals(c.get("typ"))) {
      throw new BadCredentialsException("WRONG_TOKEN_TYPE");
    }
    String username = c.getSubject();
    String tcode = (tenantCode == null || tenantCode.isBlank()) ? defaultTenantCode : tenantCode;
    UserAuthView user;
    try {
      user = userClient.findByTenantAndUsername(tcode, username);
    } catch (RuntimeException e) {
      log.warn("switchTenant: user-service 查询失败 tenant={} username={}", tcode, username, e);
      throw new BaseException(ErrorCode.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE, e);
    }
    if (user == null || user.id() == null) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + username);
    }
    String status = user.status() == null ? "ACTIVE" : user.status();
    if (!"ACTIVE".equals(status)) {
      throw new BadCredentialsException("ACCOUNT_" + status);
    }
    java.util.List<String> roles =
        user.roles() == null || user.roles().isEmpty() ? java.util.List.of("USER") : user.roles();
    String access = jwt.generateAccessToken(user.id(), user.tenantId(), tcode, username, roles);
    String newRefresh = jwt.generateRefreshToken(user.id(), username);
    markLastLogin(user.id());
    return new TokenResponse(
        access,
        newRefresh,
        jwt.getAccessTtlSeconds(),
        "Bearer",
        user.id(),
        username,
        tcode,
        user.tenantName(),
        user.tenantEdition(),
        user.passwordHash() == null || user.passwordHash().isBlank(),
        tenantOptions(user.username()));
  }
}
