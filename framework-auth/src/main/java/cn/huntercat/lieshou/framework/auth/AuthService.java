package cn.huntercat.lieshou.framework.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.LoginWithCodeRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.RefreshRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.RegisterRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.ResetPasswordRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.SendCodeRequest;
import cn.huntercat.lieshou.framework.auth.dto.AuthDtos.TokenResponse;
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

  /** 默认租户编码（未传 tenantCode 时） · ADR-0022 */
  private static final String DEFAULT_TENANT_CODE = "huntercat";

  private final JwtService jwt;
  private final UserAuthPort userClient;
  private final PasswordEncoder passwordEncoder;

  public AuthService(JwtService jwt, UserAuthPort userClient, PasswordEncoder passwordEncoder) {
    this.jwt = jwt;
    this.userClient = userClient;
    this.passwordEncoder = passwordEncoder;
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
            ? DEFAULT_TENANT_CODE
            : req.tenantCode();
    UserAuthView user;
    try {
      user = userClient.findByTenantAndUsername(tenantCode, req.username());
    } catch (Exception e) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + req.username());
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
        tenantOptions(user.username()));
  }

  /** 登录成功回写 last_login_at（失败静默，不影响登录主流程）. */
  private void markLastLogin(Long userId) {
    try {
      userClient.markLastLogin(userId);
    } catch (Exception ignored) {
      // 回写失败不阻断登录（user-service 暂不可达时降级）
    }
  }

  // ============================================================
  // Phase 8 · 认证体系扩展（ADR-0023）：验证码登录 / 注册 / 重置密码
  // ============================================================

  /** 发送验证码（短信/邮箱） */
  public void sendCode(SendCodeRequest req) {
    try {
      userClient.sendVerificationCode(
          Map.of("channel", req.channel(), "target", req.target(), "purpose", req.purpose()));
    } catch (Exception e) {
      throw new BadCredentialsException("SEND_CODE_FAILED");
    }
  }

  /** 验证码登录：校验 code → 按 phone/email 查用户 → JWT */
  public TokenResponse loginWithCode(LoginWithCodeRequest req) {
    verifyCode(req.channel(), req.target(), "LOGIN", req.code());
    UserAuthView user = findUserByTarget(req.channel(), req.target());
    if (user == null || user.id() == null) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + req.target());
    }
    return issueTokens(user, req.tenantCode());
  }

  /** 注册（验证码）→ 创建用户 → 注册即登录 */
  public TokenResponse register(RegisterRequest req) {
    verifyCode(req.channel(), req.target(), "REGISTER", req.code());
    Map<String, String> createBody = new java.util.HashMap<>();
    createBody.put("username", req.username());
    createBody.put("displayName", req.displayName());
    createBody.put("password", req.password());
    if (req.inviteCode() != null && !req.inviteCode().isBlank()) {
      // 邀请注册：租户/角色由 user-service 按邀请码解析（ADR-0023 P2）
      createBody.put("inviteCode", req.inviteCode());
    } else {
      createBody.put(
          "tenantCode", req.tenantCode() == null ? DEFAULT_TENANT_CODE : req.tenantCode());
    }
    if ("SMS".equals(req.channel())) {
      createBody.put("phone", req.target());
    } else {
      createBody.put("email", req.target());
    }
    Map<String, Object> created;
    try {
      created = userClient.createUser(createBody);
    } catch (IllegalArgumentException e) {
      throw new BadCredentialsException(
          e.getMessage() == null ? "REGISTER_FAILED" : e.getMessage());
    } catch (Exception e) {
      throw new BadCredentialsException("REGISTER_FAILED");
    }
    Number uid = (Number) created.get("id");
    Number tid = (Number) created.get("tenantId");
    String tcode = (String) created.getOrDefault("tenantCode", DEFAULT_TENANT_CODE);
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
        tenantOptions(req.username()));
  }

  /** 忘记密码：校验 code → 按 phone/email 查用户 → 改密 */
  public void resetPassword(ResetPasswordRequest req) {
    verifyCode(req.channel(), req.target(), "RESET_PASSWORD", req.code());
    UserAuthView user = findUserByTarget(req.channel(), req.target());
    if (user == null || user.id() == null) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + req.target());
    }
    try {
      userClient.updateUserPassword(user.id(), Map.of("password", req.newPassword()));
    } catch (Exception e) {
      throw new BadCredentialsException("RESET_FAILED");
    }
  }

  /** 校验验证码（失败 → BadCredentialsException） */
  private void verifyCode(String channel, String target, String purpose, String code) {
    try {
      userClient.verifyVerificationCode(
          Map.of("channel", channel, "target", target, "purpose", purpose, "code", code));
    } catch (Exception e) {
      throw new BadCredentialsException("INVALID_CODE");
    }
  }

  /** 按渠道查用户（SMS→phone / EMAIL→email） */
  private UserAuthView findUserByTarget(String channel, String target) {
    try {
      if ("SMS".equals(channel)) {
        return userClient.findByPhone(target);
      }
      return userClient.findByEmail(target);
    } catch (Exception e) {
      return null;
    }
  }

  /** 签发 access + refresh（含租户维度） */
  private TokenResponse issueTokens(UserAuthView user, String tenantCode) {
    String tcode =
        (tenantCode == null || tenantCode.isBlank())
            ? (user.tenantCode() == null ? DEFAULT_TENANT_CODE : user.tenantCode())
            : tenantCode;
    List<String> roles =
        user.roles() == null || user.roles().isEmpty() ? List.of("USER") : user.roles();
    String access =
        jwt.generateAccessToken(user.id(), user.tenantId(), tcode, user.username(), roles);
    String refresh = jwt.generateRefreshToken(user.id(), user.username());
    markLastLogin(user.id());
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

  /** 按用户名查可登录租户选项（多租户登录前 · tenant-options 端点） */
  public java.util.List<java.util.Map<String, Object>> tenantOptions(String username) {
    try {
      return userClient.tenantOptions(username);
    } catch (Exception e) {
      // 服务不可达/查询失败 → 空列表，前端回退默认租户登录
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
    String tcode = (tenantCode == null || tenantCode.isBlank()) ? DEFAULT_TENANT_CODE : tenantCode;
    UserAuthView user;
    try {
      user = userClient.findByTenantAndUsername(tcode, username);
    } catch (Exception e) {
      throw new UsernameNotFoundException("USER_NOT_FOUND: " + username);
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
        tenantOptions(user.username()));
  }
}
