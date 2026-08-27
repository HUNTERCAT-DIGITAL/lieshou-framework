package cn.huntercat.lieshou.framework.service;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.domain.Role;
import cn.huntercat.lieshou.framework.domain.RoleRepository;
import cn.huntercat.lieshou.framework.domain.Tenant;
import cn.huntercat.lieshou.framework.domain.TenantInvite;
import cn.huntercat.lieshou.framework.domain.TenantInviteRepository;
import cn.huntercat.lieshou.framework.domain.TenantRepository;
import cn.huntercat.lieshou.framework.domain.User;
import cn.huntercat.lieshou.framework.domain.UserRepository;
import java.util.List;
import java.util.Optional;

/** UserService 单测（ADR-0044 阶段 3 · 业务唯一源锁定）。 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository repo;
  @Mock private TenantRepository tenantRepo;
  @Mock private TenantInviteRepository inviteRepo;
  @Mock private RoleRepository roleRepo;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private UserService service;

  private static Tenant activeTenant(Long id) {
    Tenant t = new Tenant("Acme", "acme");
    t.setStatus(Tenant.Status.ACTIVE);
    if (id != null) {
      org.springframework.test.util.ReflectionTestUtils.setField(t, "id", id);
    }
    return t;
  }

  private static TenantInvite invite(Long tenantId, String role, boolean valid) {
    TenantInvite inv = new TenantInvite(tenantId, "INV12345", role, null, null, null);
    if (!valid) {
      inv.consume(); // revoked 或 used
    }
    return inv;
  }

  private static User user(Long id, Long tenantId, String username) {
    User u = new User();
    u.setId(id);
    u.setTenantId(tenantId);
    u.setUsername(username);
    return u;
  }

  @Test
  void create_常规注册默认租户huntercat() {
    when(tenantRepo.findByCode("huntercat")).thenReturn(Optional.of(activeTenant(null)));
    when(repo.existsByTenantIdAndUsername(any(), anyString())).thenReturn(false);
    when(roleRepo.findByCode("USER"))
        .thenReturn(Optional.of(new Role("USER", "用户", Role.Scope.TENANT, null, true)));
    when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");
    when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    UserService.CreateResult r =
        service.create("u1", "用户1", "secret123", null, null, null, null, null);

    assertThat(r.user().getUsername()).isEqualTo("u1");
    assertThat(r.user().getTenantId()).isEqualTo(r.tenant().getId());
    assertThat(r.user().getPasswordHash()).isEqualTo("hashed-secret");
    verify(repo).save(any(User.class));
  }

  @Test
  void create_邀请码优先解析租户与角色() {
    when(inviteRepo.findByCode("INV12345")).thenReturn(Optional.of(invite(7L, "ADMIN", true)));
    when(tenantRepo.findById(7L)).thenReturn(Optional.of(activeTenant(7L)));
    when(repo.existsByTenantIdAndUsername(any(), anyString())).thenReturn(false);
    when(roleRepo.findByCode("ADMIN"))
        .thenReturn(Optional.of(new Role("ADMIN", "管理员", Role.Scope.TENANT, null, true)));
    when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    UserService.CreateResult r =
        service.create("u2", "用户2", "secret123", null, null, null, "INV12345", null);

    assertThat(r.user().getTenantId()).isEqualTo(7L);
    assertThat(r.user().getRoles().get(0).getCode()).isEqualTo("ADMIN");
  }

  @Test
  void create_无效邀请码抛INVALID_INVITE() {
    when(inviteRepo.findByCode("BAD")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.create("u", "u", "secret123", null, null, null, "BAD", null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("INVALID_INVITE"));
  }

  @Test
  void create_邀请码与强制租户不匹配抛INVITE_TENANT_MISMATCH() {
    when(inviteRepo.findByCode("INV12345")).thenReturn(Optional.of(invite(7L, "USER", true)));
    assertThatThrownBy(
            () -> service.create("u", "u", "secret123", null, null, null, "INV12345", 9L))
        .isInstanceOf(BaseException.class)
        .satisfies(
            e -> assertThat(((BaseException) e).errorCode()).isEqualTo("INVITE_TENANT_MISMATCH"));
  }

  @Test
  void create_租户停用抛TENANT_NOT_ACTIVE() {
    when(inviteRepo.findByCode("INV12345")).thenReturn(Optional.of(invite(7L, "USER", true)));
    Tenant t = new Tenant("x", "acme");
    t.setStatus(Tenant.Status.DISABLED);
    when(tenantRepo.findById(7L)).thenReturn(Optional.of(t));

    assertThatThrownBy(
            () -> service.create("u", "u", "secret123", null, null, null, "INV12345", null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("TENANT_NOT_ACTIVE"));
  }

  @Test
  void create_用户名查重抛USERNAME_TAKEN() {
    when(tenantRepo.findByCode("huntercat")).thenReturn(Optional.of(activeTenant(null)));
    when(repo.existsByTenantIdAndUsername(any(), anyString())).thenReturn(true);

    assertThatThrownBy(
            () -> service.create("dup", "dup", "secret123", null, null, null, null, null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("USERNAME_TAKEN"));
    verify(repo, never()).save(any(User.class));
  }

  @Test
  void get_跨租户访问404不泄露() {
    when(repo.findById(1L)).thenReturn(Optional.of(user(1L, 7L, "u")));
    assertThatThrownBy(() -> service.get(1L, 9L))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void update_非法状态抛INVALID_STATUS() {
    when(repo.findById(1L)).thenReturn(Optional.of(user(1L, 7L, "u")));
    assertThatThrownBy(() -> service.update(1L, null, null, null, null, "PAUSED", null, null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("INVALID_STATUS"));
  }

  @Test
  void update_提供密码则重新编码() {
    User u = user(1L, 7L, "u");
    when(repo.findById(1L)).thenReturn(Optional.of(u));
    when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    when(passwordEncoder.encode("newpass123")).thenReturn("hashed-new");
    User saved = service.update(1L, null, "新名", null, null, null, null, "newpass123");

    assertThat(saved.getDisplayName()).isEqualTo("新名");
    assertThat(saved.getPasswordHash()).isEqualTo("hashed-new");
  }

  @Test
  void authView_租户停用抛TENANT_DISABLED() {
    Tenant t = new Tenant("x", "acme");
    t.setStatus(Tenant.Status.DISABLED);
    when(tenantRepo.findByCode("acme")).thenReturn(Optional.of(t));

    assertThatThrownBy(() -> service.authViewByTenantAndUsername("acme", "u"))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("TENANT_DISABLED"));
  }

  @Test
  void authView_组装角色码与状态() {
    when(tenantRepo.findByCode("acme")).thenReturn(Optional.of(activeTenant(null)));
    User u = user(1L, 1L, "u");
    u.setRoles(List.of(new Role("ADMIN", "管理员", Role.Scope.TENANT, null, true)));
    u.setPasswordHash("hash");
    when(repo.findByTenantIdAndUsername(any(), eq("u"))).thenReturn(Optional.of(u));

    var view = service.authViewByTenantAndUsername("acme", "u");

    assertThat(view.roles()).containsExactly("ADMIN");
    assertThat(view.passwordHash()).isEqualTo("hash");
    assertThat(view.tenantCode()).isEqualTo("acme");
  }

  @Test
  void delete_租户维度删除() {
    when(repo.findById(1L)).thenReturn(Optional.of(user(1L, 7L, "u")));
    User deleted = service.delete(1L, null);
    assertThat(deleted.getUsername()).isEqualTo("u");
    verify(repo).deleteById(1L);
  }

  @Test
  void markLastLogin_幂等静默() {
    when(repo.findById(99L)).thenReturn(Optional.empty());
    service.markLastLogin(99L); // 不抛
    verify(repo, never()).save(any(User.class));
  }
}
