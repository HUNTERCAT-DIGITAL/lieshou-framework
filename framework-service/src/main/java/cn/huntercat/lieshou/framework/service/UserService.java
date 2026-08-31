package cn.huntercat.lieshou.framework.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import cn.huntercat.lieshou.framework.common.dto.UserAuthView;
import cn.huntercat.lieshou.framework.domain.Role;
import cn.huntercat.lieshou.framework.domain.RoleRepository;
import cn.huntercat.lieshou.framework.domain.Tenant;
import cn.huntercat.lieshou.framework.domain.TenantInvite;
import cn.huntercat.lieshou.framework.domain.TenantInviteRepository;
import cn.huntercat.lieshou.framework.domain.TenantRepository;
import cn.huntercat.lieshou.framework.domain.User;
import cn.huntercat.lieshou.framework.domain.UserRepository;
import java.time.Instant;
import java.util.List;

/**
 * 用户生命周期业务（多租户 · ADR-0022 · 三套产品线共用）.
 *
 * <p>从两端 Controller 内联收敛（ADR-0044 阶段 3）：创建（邀请码/租户强制/常规三分支 + 查重 + 密码编码）、更新（字段可选 + 枚举校验 +
 * 角色解析）、删除、认证查询（auth view / 租户停用阻断 / 手机邮箱）、lastLogin 回写。同时是 {@code UserAuthPort.createUser} 的
 * 唯一实现（消除端口版与 controller 版的分叉——统一默认租户 huntercat + 邀请码支持）。
 *
 * <p>错误抛 {@link BaseException}（错误码契约：INVALID_INVITE / INVITE_TENANT_MISMATCH / TENANT_NOT_ACTIVE /
 * TENANT_NOT_FOUND / USERNAME_TAKEN / INVALID_STATUS / TENANT_DISABLED / NOT_FOUND）；PLATFORM_ADMIN
 * 权限校验与审计记录属 HTTP 层，保留在薄壳 Controller。
 */
@Service
public class UserService {

  /** 默认租户编码（兼容未显式传租户的调用） · ADR-0022 */
  private static final String DEFAULT_TENANT_CODE = "huntercat";

  private final UserRepository repo;
  private final TenantRepository tenantRepo;
  private final TenantInviteRepository inviteRepo;
  private final RoleRepository roleRepo;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository repo,
      TenantRepository tenantRepo,
      TenantInviteRepository inviteRepo,
      RoleRepository roleRepo,
      PasswordEncoder passwordEncoder) {
    this.repo = repo;
    this.tenantRepo = tenantRepo;
    this.inviteRepo = inviteRepo;
    this.roleRepo = roleRepo;
    this.passwordEncoder = passwordEncoder;
  }

  /** 创建用户结果（User + 归属租户，供 controller 组装响应 / 端口适配组装）。 */
  public record CreateResult(User user, Tenant tenant) {}

  /**
   * 创建用户（三分支：邀请码优先 → 租户强制 → 常规注册默认 huntercat）.
   *
   * @param forcedTenantId 租户内请求强制租户（X-Tenant-Id）；null = 非租户内请求
   */
  @Transactional
  public CreateResult create(
      String username,
      String displayName,
      String password,
      String email,
      String phone,
      String tenantCode,
      String inviteCode,
      Long forcedTenantId) {
    if (password != null && !password.isBlank()) {
      validatePassword(password);
    }
    Tenant tenant;
    String role = "USER";
    if (inviteCode != null && !inviteCode.isBlank()) {
      // —— 邀请码优先（ADR-0023 Phase 2）：租户/角色来自邀请码 ——
      TenantInvite invite =
          inviteRepo
              .findByCode(inviteCode)
              .orElseThrow(
                  () -> new BaseException("INVALID_INVITE", HttpStatus.BAD_REQUEST, "邀请码无效"));
      if (!invite.isValid()) {
        throw new BaseException("INVALID_INVITE", HttpStatus.BAD_REQUEST, "邀请码无效或已使用");
      }
      if (forcedTenantId != null && !invite.getTenantId().equals(forcedTenantId)) {
        throw new BaseException("INVITE_TENANT_MISMATCH", HttpStatus.FORBIDDEN, "邀请码与当前租户不匹配");
      }
      tenant =
          tenantRepo
              .findById(invite.getTenantId())
              .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "租户不存在"));
      requireActive(tenant);
      role = invite.getRole();
      invite.consume();
      inviteRepo.save(invite);
    } else if (forcedTenantId != null) {
      // —— 租户内请求强制：只能用请求的租户创建（忽略 tenantCode）——
      tenant =
          tenantRepo
              .findById(forcedTenantId)
              .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "租户不存在"));
      requireActive(tenant);
    } else {
      // —— 常规注册：tenantCode 指定租户（默认 huntercat）——
      String code = (tenantCode == null || tenantCode.isBlank()) ? DEFAULT_TENANT_CODE : tenantCode;
      tenant =
          tenantRepo
              .findByCode(code)
              .orElseThrow(
                  () ->
                      new BaseException(
                          "TENANT_NOT_FOUND", HttpStatus.BAD_REQUEST, "租户不存在: " + code));
    }
    if (repo.existsByTenantIdAndUsername(tenant.getId(), username)) {
      throw new BaseException("USERNAME_TAKEN", HttpStatus.BAD_REQUEST, "用户名已被占用");
    }
    User u = new User();
    u.setTenantId(tenant.getId());
    u.setUsername(username);
    u.setDisplayName(displayName);
    u.setEmail(email);
    u.setPhone(phone);
    u.setPasswordHash(
        password != null && !password.isBlank() ? passwordEncoder.encode(password) : null);
    u.setRoles(List.of(roleByCode(role)));
    User saved = repo.save(u);
    return new CreateResult(saved, tenant);
  }

  /** 租户内用户列表（tid 为 null = 平台跨租户视图，权限由 controller 校验）。 */
  @Transactional(readOnly = true)
  public List<User> list(Long tenantId) {
    return tenantId == null ? repo.findAll() : repo.findByTenantId(tenantId);
  }

  /** 用户数（tid 为 null = 全平台）。 */
  @Transactional(readOnly = true)
  public long count(Long tenantId) {
    return tenantId == null ? repo.count() : repo.countByTenantId(tenantId);
  }

  /** 按 id 查用户（租户维度；跨租户 404 不泄露存在性）。 */
  @Transactional(readOnly = true)
  public User get(Long id, Long tenantId) {
    return repo.findById(id)
        .filter(u -> tenantMatches(u, tenantId))
        .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "用户不存在"));
  }

  /** 更新用户（字段均可选；status/edition 枚举非法抛 INVALID_STATUS）。 */
  @Transactional
  public User update(
      Long id,
      Long tenantId,
      String displayName,
      String email,
      String phone,
      String status,
      String[] roles,
      String password) {
    User u = get(id, tenantId);
    if (displayName != null && !displayName.isBlank()) {
      u.setDisplayName(displayName);
    }
    if (email != null && !email.isBlank()) {
      u.setEmail(email);
    }
    if (phone != null && !phone.isBlank()) {
      u.setPhone(phone);
    }
    if (status != null && !status.isBlank()) {
      try {
        u.setStatus(User.Status.valueOf(status));
      } catch (IllegalArgumentException e) {
        throw new BaseException("INVALID_STATUS", HttpStatus.BAD_REQUEST, "用户状态不合法");
      }
    }
    if (roles != null && roles.length > 0) {
      // 未知角色码报错（不再静默丢弃——避免误传角色码被悄悄忽略）
      List<Role> newRoles = new java.util.ArrayList<>();
      for (String code : roles) {
        Role role = roleByCode(code);
        if (role == null) {
          throw new BaseException("INVALID_ROLE", HttpStatus.BAD_REQUEST, "角色不存在: " + code);
        }
        newRoles.add(role);
      }
      u.setRoles(newRoles);
    }
    if (password != null && !password.isBlank()) {
      if (password != null && !password.isBlank()) {
      validatePassword(password);
    }
      u.setPasswordHash(
        password != null && !password.isBlank() ? passwordEncoder.encode(password) : null);
    }
    return repo.save(u);
  }

  /** 密码策略（2026-09 统一）：至少 8 位且同时包含字母和数字。 */
  private static void validatePassword(String password) {
    boolean strong =
        password != null
            && password.length() >= 8
            && password.chars().anyMatch(Character::isLetter)
            && password.chars().anyMatch(Character::isDigit);
    if (!strong) {
      throw new BaseException("WEAK_PASSWORD", HttpStatus.BAD_REQUEST, "密码至少 8 位且包含字母和数字");
    }
  }

  /**
   * 自助修改密码（本人操作 · 校验原密码 · ADR-0044 阶段 3+）。
   *
   * <p>错误契约：USER_NOT_FOUND / OLD_PASSWORD_MISMATCH / INVALID_PASSWORD。 校验通过后编码入库，不影响租户/角色等其余字段；供
   * /users/me/password 薄壳端点调用。
   */
  @Transactional
  public void changePassword(Long userId, String oldPassword, String newPassword) {
    User u =
        repo.findById(userId).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "用户不存在"));
    if (oldPassword == null || !passwordEncoder.matches(oldPassword, u.getPasswordHash())) {
      throw new BaseException("OLD_PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST, "原密码不正确");
    }
    validatePassword(newPassword);
    u.setPasswordHash(passwordEncoder.encode(newPassword));
    repo.save(u);
  }

  /** 删除用户（租户维度）。 */
  @Transactional
  public User delete(Long id, Long tenantId) {
    User u = get(id, tenantId);
    repo.deleteById(id);
    return u;
  }

  /** 按 username 查（admin 模块本地查询；不存在 → NOT_FOUND）。 */
  @Transactional(readOnly = true)
  public User findByUsername(String username) {
    return repo.findByUsername(username)
        .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "用户不存在"));
  }

  /** 认证视图（租户 + username；租户停用阻断登录 → TENANT_DISABLED）。 */
  @Transactional(readOnly = true)
  public UserAuthView authViewByTenantAndUsername(String tenantCode, String username) {
    Tenant tenant =
        tenantRepo
            .findByCode(tenantCode)
            .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "租户不存在"));
    if (tenant.getStatus() != Tenant.Status.ACTIVE) {
      throw new BaseException("TENANT_DISABLED", HttpStatus.FORBIDDEN, "租户已停用");
    }
    User u =
        repo.findByTenantIdAndUsername(tenant.getId(), username)
            .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "用户不存在"));
    return toAuthView(u, tenant);
  }

  /** 认证视图（手机号）。 */
  @Transactional(readOnly = true)
  public UserAuthView authViewByPhone(String phone) {
    return repo.findByPhone(phone)
        .map(this::toAuthViewWithTenant)
        .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "用户不存在"));
  }

  /** 认证视图（邮箱）。 */
  @Transactional(readOnly = true)
  public UserAuthView authViewByEmail(String email) {
    return repo.findByEmail(email)
        .map(this::toAuthViewWithTenant)
        .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "用户不存在"));
  }

  /** 登录成功回写 lastLogin（幂等：不存在静默忽略）。 */
  @Transactional
  public void markLastLogin(Long id) {
    repo.findById(id)
        .ifPresent(
            u -> {
              u.setLastLoginAt(Instant.now());
              repo.save(u);
            });
  }

  /**
   * 跨租户查该 username 可登录的租户（用户 ACTIVE 且租户 ACTIVE）.
   *
   * <p>供登录页同用户名多租户选择；仅返回租户 code/name/edition，无敏感信息。 统一微服务版 REST（findAllByUsername）与端口版（取首个）的分叉。
   */
  @Transactional(readOnly = true)
  public List<java.util.Map<String, Object>> tenantOptions(String username) {
    List<User> users = repo.findAllByUsername(username);
    if (users.isEmpty()) {
      return List.of();
    }
    java.util.Map<Long, Tenant> tenantsById =
        tenantRepo.findAllById(users.stream().map(User::getTenantId).distinct().toList()).stream()
            .collect(java.util.stream.Collectors.toMap(Tenant::getId, t -> t));
    return users.stream()
        .filter(u -> u.getStatus() == null || u.getStatus() == User.Status.ACTIVE)
        .map(User::getTenantId)
        .distinct()
        .map(tenantsById::get)
        .filter(java.util.Objects::nonNull)
        .filter(t -> t.getStatus() == null || t.getStatus() == Tenant.Status.ACTIVE)
        .map(
            t ->
                java.util.Map.<String, Object>of(
                    "tenantId",
                    t.getId(),
                    "tenantCode",
                    t.getCode(),
                    "tenantName",
                    t.getName(),
                    "tenantEdition",
                    t.getEdition() == null ? null : t.getEdition().name()))
        .toList();
  }

  // ============================================================
  // 工具
  // ============================================================

  private void requireActive(Tenant tenant) {
    if (tenant.getStatus() != Tenant.Status.ACTIVE) {
      throw new BaseException("TENANT_NOT_ACTIVE", HttpStatus.BAD_REQUEST, "租户未启用");
    }
  }

  private boolean tenantMatches(User u, Long tenantHeader) {
    return tenantHeader == null || u.getTenantId().equals(tenantHeader);
  }

  private Role roleByCode(String code) {
    return roleRepo.findByCode(code).orElse(null);
  }

  private UserAuthView toAuthViewWithTenant(User u) {
    Tenant tenant = tenantRepo.findById(u.getTenantId()).orElse(null);
    return toAuthView(u, tenant);
  }

  /** 组装 UserAuthView（含租户编码 + 角色 codes）。 */
  private UserAuthView toAuthView(User u, Tenant tenant) {
    List<String> roleCodes =
        u.getRoles() == null || u.getRoles().isEmpty()
            ? List.of("USER")
            : u.getRoles().stream().map(Role::getCode).toList();
    return new UserAuthView(
        u.getId(),
        u.getTenantId(),
        tenant == null ? null : tenant.getCode(),
        tenant == null ? null : tenant.getName(),
        tenant == null || tenant.getEdition() == null ? null : tenant.getEdition().name(),
        u.getUsername(),
        u.getDisplayName(),
        u.getPasswordHash(),
        roleCodes,
        u.getStatus() == null ? "ACTIVE" : u.getStatus().name());
  }
}
