package cn.huntercat.lieshou.framework.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.domain.Tenant;
import cn.huntercat.lieshou.framework.domain.TenantInvite;
import cn.huntercat.lieshou.framework.domain.TenantInviteRepository;
import cn.huntercat.lieshou.framework.domain.TenantRepository;

/** TenantInviteService 单测（ADR-0044 阶段 1 · 业务唯一源锁定）。 */
@ExtendWith(MockitoExtension.class)
class TenantInviteServiceTest {

  @Mock private TenantRepository tenantRepo;
  @Mock private TenantInviteRepository inviteRepo;
  @InjectMocks private TenantInviteService service;

  private static Tenant tenant(Long id) {
    Tenant t = new Tenant("测试租户", "test");
    return t;
  }

  @Test
  void create_缺省角色为USER且生成8位唯一码() {
    when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant(1L)));
    when(inviteRepo.findByCode(anyString())).thenReturn(Optional.empty());
    when(inviteRepo.save(any(TenantInvite.class))).thenAnswer(inv -> inv.getArgument(0));

    TenantInvite inv = service.create(1L, null, 7, 10);

    assertThat(inv.getRole()).isEqualTo("USER");
    assertThat(inv.getCode()).hasSize(8);
    assertThat(inv.getExpiresAt()).isAfter(Instant.now());
    assertThat(inv.getMaxUses()).isEqualTo(10);
    assertThat(inv.getRevokedAt()).isNull();
  }

  @Test
  void create_非法角色抛INVALID_ROLE() {
    when(tenantRepo.findById(1L)).thenReturn(Optional.of(tenant(1L)));

    assertThatThrownBy(() -> service.create(1L, "SUPER", null, null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("INVALID_ROLE"));
    verify(inviteRepo, never()).save(any(TenantInvite.class));
  }

  @Test
  void create_租户不存在抛NOT_FOUND() {
    when(tenantRepo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(99L, null, null, null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void list_租户不存在抛NOT_FOUND() {
    when(tenantRepo.findById(1L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.list(1L)).isInstanceOf(BaseException.class);
  }

  @Test
  void revoke_标记revokedAt() {
    TenantInvite inv = new TenantInvite(1L, "CODE1234", "USER", null, null, null);
    when(inviteRepo.findById(7L)).thenReturn(Optional.of(inv));

    service.revoke(1L, 7L);

    assertThat(inv.getRevokedAt()).isNotNull();
    verify(inviteRepo).save(inv);
  }

  @Test
  void revoke_租户不匹配抛NOT_FOUND() {
    TenantInvite inv = new TenantInvite(2L, "CODE1234", "USER", null, null, null);
    when(inviteRepo.findById(7L)).thenReturn(Optional.of(inv));

    assertThatThrownBy(() -> service.revoke(1L, 7L))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("NOT_FOUND"));
    verify(inviteRepo, never()).save(any(TenantInvite.class));
  }

  @Test
  void revoke_不存在抛NOT_FOUND() {
    when(inviteRepo.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.revoke(1L, 99L)).isInstanceOf(BaseException.class);
  }
}
