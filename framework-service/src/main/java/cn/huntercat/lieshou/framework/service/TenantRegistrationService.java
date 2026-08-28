package cn.huntercat.lieshou.framework.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.domain.Role;
import cn.huntercat.lieshou.framework.domain.RoleRepository;
import cn.huntercat.lieshou.framework.domain.Tenant;
import cn.huntercat.lieshou.framework.domain.TenantRepository;
import cn.huntercat.lieshou.framework.domain.User;
import cn.huntercat.lieshou.framework.domain.UserRepository;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 租户自助开通（SaaS 增长路径：官网注册 → 自动建租户 + 管理员 · issue #24）.
 *
 * <p>与后台 {@code TenantController.create}（PLATFORM_ADMIN 手工开通）互补：本服务无鉴权依赖，公开端点 {@code POST
 * /api/tenants/register} 调用。租户 + 管理员用户在同一事务创建（原子），失败整体回滚。
 *
 * <p>注册即开通（租户 ACTIVE 可直接登录）；版别默认 GENERIC（行业版由平台线下销售/配置，ADR-0035 客户差异进配置层）。
 */
@Service
public class TenantRegistrationService {

  /** 租户编码格式：小写字母 / 数字 / 连字符，2-32 位 */
  private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");

  private final TenantRepository tenants;
  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder encoder;

  public TenantRegistrationService(
      PasswordEncoder encoder,
      TenantRepository tenants,
      UserRepository users,
      RoleRepository roles) {
    this.tenants = tenants;
    this.users = users;
    this.roles = roles;
    this.encoder = encoder;
  }

  /** 注册结果（登录页预填租户编码 + 用户名） */
  public record RegistrationResult(Tenant tenant, String adminUsername, String adminDisplayName) {}

  @Transactional
  public RegistrationResult register(
      String tenantName,
      String tenantCode,
      String username,
      String displayName,
      String password,
      String email) {
    // —— 租户校验 ——
    if (tenantName == null || tenantName.isBlank()) {
      throw new IllegalArgumentException("租户名称不能为空");
    }
    if (tenantName.length() > 128) {
      throw new IllegalArgumentException("租户名称长度不能超过 128");
    }
    if (tenantCode == null || !CODE_PATTERN.matcher(tenantCode).matches()) {
      throw new IllegalArgumentException("租户编码须为 2-32 位小写字母/数字/连字符（如 mycompany）");
    }
    if (tenants.findByCode(tenantCode).isPresent()) {
      throw new IllegalArgumentException("租户编码已被占用: " + tenantCode);
    }
    // —— 管理员校验 ——
    if (username == null || username.isBlank() || username.length() > 64) {
      throw new IllegalArgumentException("管理员用户名不能为空且不超过 64 位");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("管理员姓名不能为空");
    }
    if (!isStrongPassword(password)) {
      throw new IllegalArgumentException("密码至少 8 位且包含字母和数字");
    }

    // —— 原子创建：租户 + 管理员 ——
    Tenant tenant = tenants.save(new Tenant(tenantName.trim(), tenantCode.trim()));

    Role adminRole =
        roles
            .findByCode("TENANT_ADMIN")
            .orElseThrow(() -> new IllegalStateException("系统角色 TENANT_ADMIN 缺失"));

    User admin = new User();
    admin.setTenantId(tenant.getId());
    admin.setUsername(username.trim());
    admin.setDisplayName(displayName.trim());
    admin.setEmail(email == null || email.isBlank() ? null : email.trim());
    admin.setPasswordHash(encoder.encode(password));
    admin.setStatus(User.Status.ACTIVE);
    admin.setRoles(List.of(adminRole));
    users.save(admin);

    return new RegistrationResult(tenant, admin.getUsername(), admin.getDisplayName());
  }

  /** 密码策略（与 UserService.validatePassword 一致）：至少 8 位且同时包含字母和数字。 */
  private static boolean isStrongPassword(String password) {
    return password != null
        && password.length() >= 8
        && password.chars().anyMatch(Character::isLetter)
        && password.chars().anyMatch(Character::isDigit);
  }
}
