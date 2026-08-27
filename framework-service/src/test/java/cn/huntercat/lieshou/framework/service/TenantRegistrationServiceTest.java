package cn.huntercat.lieshou.framework.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.service.TenantRegistrationService;
import cn.huntercat.lieshou.framework.domain.Role;
import cn.huntercat.lieshou.framework.domain.RoleRepository;
import cn.huntercat.lieshou.framework.domain.Tenant;
import cn.huntercat.lieshou.framework.domain.TenantRepository;
import cn.huntercat.lieshou.framework.domain.User;
import cn.huntercat.lieshou.framework.domain.UserRepository;
import java.util.Optional;

/**
 * 租户自助开通服务单测（Mockito 纯单测 · issue #24）.
 *
 * <p>覆盖：成功注册（租户 + 管理员 + TENANT_ADMIN）、编码格式/占用校验、密码强度、失败不落库。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantRegistrationService（租户自助开通）")
class TenantRegistrationServiceTest {

  @Mock private TenantRepository tenants;
  @Mock private UserRepository users;
  @Mock private RoleRepository roles;
  private TenantRegistrationService service;

  private final Tenant savedTenant = new Tenant("示例公司", "sampleco");

  @BeforeEach
  void setUp() {
    service = new TenantRegistrationService(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(), tenants, users, roles);
    lenient().when(tenants.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    Role admin = new Role("TENANT_ADMIN", "租户管理员", null, null, true);
    lenient().when(roles.findByCode("TENANT_ADMIN")).thenReturn(Optional.of(admin));
  }

  @Test
  @DisplayName("成功注册：创建租户（ACTIVE + GENERIC）+ 管理员（TENANT_ADMIN + BCrypt 密码）")
  void register_success() {
    TenantRegistrationService.RegistrationResult result =
        service.register("示例公司", "sampleco", "admin", "管理员", "secret123", "admin@x.cn");

    verify(tenants).save(any(Tenant.class));
    verify(users).save(any(User.class));
    assertThat(result.tenant().getCode()).isEqualTo("sampleco");
    assertThat(result.tenant().getEdition()).isEqualTo(Tenant.Edition.GENERIC);
    assertThat(result.adminUsername()).isEqualTo("admin");
    // 密码被 BCrypt 加密存储
    org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(users).save(captor.capture());
    assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("secret123");
    assertThat(
            new BCryptPasswordEncoder().matches("secret123", captor.getValue().getPasswordHash()))
        .isTrue();
    assertThat(captor.getValue().getRoles())
        .extracting(Role::getCode)
        .containsExactly("TENANT_ADMIN");
  }

  @Test
  @DisplayName("租户编码格式非法 → 拒绝")
  void register_badCode_rejected() {
    assertThatThrownBy(() -> service.register("公司", "Bad Code!", "admin", "管理员", "secret123", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("租户编码");
  }

  @Test
  @DisplayName("租户编码已占用 → 拒绝")
  void register_codeTaken_rejected() {
    when(tenants.findByCode("taken")).thenReturn(Optional.of(savedTenant));

    assertThatThrownBy(() -> service.register("公司", "taken", "admin", "管理员", "secret123", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("已被占用");
  }

  @Test
  @DisplayName("密码 < 6 位 → 拒绝")
  void register_weakPassword_rejected() {
    assertThatThrownBy(() -> service.register("公司", "sampleco", "admin", "管理员", "123", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("密码至少 6 位");
  }

  @Test
  @DisplayName("编码占用时租户未创建（校验先于落库）")
  void register_codeTaken_noTenantSaved() {
    when(tenants.findByCode("taken")).thenReturn(Optional.of(savedTenant));

    assertThatThrownBy(() -> service.register("公司", "taken", "admin", "管理员", "secret123", null))
        .isInstanceOf(IllegalArgumentException.class);
    org.mockito.Mockito.verify(tenants, org.mockito.Mockito.never()).save(any(Tenant.class));
  }
}
