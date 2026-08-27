package cn.huntercat.lieshou.framework.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.domain.Tenant;
import cn.huntercat.lieshou.framework.domain.TenantRepository;
import cn.huntercat.lieshou.framework.domain.UserRepository;
import java.util.Optional;

/** TenantService 单测（ADR-0044 阶段 2 · 业务唯一源锁定）。 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

  @Mock private TenantRepository repo;
  @Mock private UserRepository userRepo;
  @Mock private TenantRegistrationService registration;
  @InjectMocks private TenantService service;

  @Test
  void create_缺省版别为GENERIC() {
    when(repo.findByCode("acme")).thenReturn(Optional.empty());
    when(repo.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    Tenant t = service.create("Acme", "acme", null);

    assertThat(t.getCode()).isEqualTo("acme");
    assertThat(t.getEdition()).isEqualTo(Tenant.Edition.GENERIC);
    assertThat(t.getStatus()).isEqualTo(Tenant.Status.ACTIVE);
  }

  @Test
  void create_code冲突抛TENANT_CODE_TAKEN() {
    when(repo.findByCode("acme")).thenReturn(Optional.of(new Tenant("x", "acme")));

    assertThatThrownBy(() -> service.create("Acme", "acme", null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("TENANT_CODE_TAKEN"));
    verify(repo, never()).save(any(Tenant.class));
  }

  @Test
  void create_非法版别抛INVALID_EDITION() {
    when(repo.findByCode("acme")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create("Acme", "acme", "NOPE"))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("INVALID_EDITION"));
  }

  @Test
  void update_仅更新提供的字段() {
    Tenant existing = new Tenant("旧名", "acme");
    when(repo.findById(1L)).thenReturn(Optional.of(existing));
    when(repo.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

    Tenant t = service.update(1L, "新名", "DISABLED", null);

    assertThat(t.getName()).isEqualTo("新名");
    assertThat(t.getStatus()).isEqualTo(Tenant.Status.DISABLED);
  }

  @Test
  void update_非法状态抛INVALID_STATUS() {
    when(repo.findById(1L)).thenReturn(Optional.of(new Tenant("x", "acme")));

    assertThatThrownBy(() -> service.update(1L, null, "PAUSED", null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("INVALID_STATUS"));
    verify(repo, never()).save(any(Tenant.class));
  }

  @Test
  void update_不存在抛NOT_FOUND() {
    when(repo.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.update(99L, null, null, null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void delete_空租户可删() {
    Tenant t = new Tenant("x", "acme");
    when(repo.findById(1L)).thenReturn(Optional.of(t));
    when(userRepo.countByTenantId(1L)).thenReturn(0L);

    Tenant deleted = service.delete(1L);

    assertThat(deleted).isSameAs(t);
    verify(repo).delete(t);
  }

  @Test
  void delete_有用户抛CONFLICT() {
    when(repo.findById(1L)).thenReturn(Optional.of(new Tenant("x", "acme")));
    when(userRepo.countByTenantId(1L)).thenReturn(3L);

    assertThatThrownBy(() -> service.delete(1L))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("CONFLICT"));
    verify(repo, never()).delete(any(Tenant.class));
  }

  @Test
  void register_非法输入转REGISTER_INVALID() {
    when(registration.register(any(), any(), any(), any(), any(), any()))
        .thenThrow(new IllegalArgumentException("租户名称不能为空"));

    assertThatThrownBy(() -> service.register("", "acme", "admin", "Admin", "secret123", null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("REGISTER_INVALID"));
  }
}
