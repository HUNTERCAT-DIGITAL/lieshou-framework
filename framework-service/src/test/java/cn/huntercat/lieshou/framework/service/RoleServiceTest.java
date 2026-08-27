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
import cn.huntercat.lieshou.framework.domain.Role;
import cn.huntercat.lieshou.framework.domain.RoleRepository;
import java.util.List;
import java.util.Optional;

/** RoleService 单测（ADR-0044 阶段 1 · 业务唯一源锁定）。 */
@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

  @Mock private RoleRepository repo;
  @InjectMocks private RoleService service;

  @Test
  void create_默认scope为TENANT且code唯一() {
    when(repo.findByCode("ops")).thenReturn(Optional.empty());
    when(repo.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    Role r = service.create("ops", "运维", null, "ops desc");

    assertThat(r.getCode()).isEqualTo("ops");
    assertThat(r.getScope()).isEqualTo(Role.Scope.TENANT);
    assertThat(r.isSystem()).isFalse();
    verify(repo).save(any(Role.class));
  }

  @Test
  void create_code冲突抛ROLE_CODE_TAKEN() {
    when(repo.findByCode("ops"))
        .thenReturn(Optional.of(new Role("ops", "x", Role.Scope.TENANT, null, false)));

    assertThatThrownBy(() -> service.create("ops", "运维", null, null))
        .isInstanceOf(BaseException.class)
        .hasMessageContaining("角色编码已存在")
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("ROLE_CODE_TAKEN"));
    verify(repo, never()).save(any(Role.class));
  }

  @Test
  void update_仅更新提供的字段() {
    Role existing = new Role("ops", "旧名", Role.Scope.TENANT, "旧描述", false);
    when(repo.findById(1L)).thenReturn(Optional.of(existing));
    when(repo.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

    Role r = service.update(1L, "新名", null, "新描述");

    assertThat(r.getName()).isEqualTo("新名");
    assertThat(r.getDescription()).isEqualTo("新描述");
    assertThat(r.getScope()).isEqualTo(Role.Scope.TENANT); // 未提供不改
  }

  @Test
  void update_system角色抛SYSTEM_ROLE_READONLY() {
    when(repo.findById(1L))
        .thenReturn(Optional.of(new Role("ADMIN", "管理员", Role.Scope.PLATFORM, null, true)));

    assertThatThrownBy(() -> service.update(1L, "x", null, null))
        .isInstanceOf(BaseException.class)
        .satisfies(
            e -> assertThat(((BaseException) e).errorCode()).isEqualTo("SYSTEM_ROLE_READONLY"));
    verify(repo, never()).save(any(Role.class));
  }

  @Test
  void update_不存在抛NOT_FOUND() {
    when(repo.findById(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(99L, "x", null, null))
        .isInstanceOf(BaseException.class)
        .satisfies(e -> assertThat(((BaseException) e).errorCode()).isEqualTo("NOT_FOUND"));
  }

  @Test
  void delete_自定义角色可删_system不可() {
    when(repo.findById(1L))
        .thenReturn(Optional.of(new Role("ops", "运维", Role.Scope.TENANT, null, false)));
    service.delete(1L);
    verify(repo).delete(any(Role.class));

    when(repo.findById(2L))
        .thenReturn(Optional.of(new Role("ADMIN", "管理员", Role.Scope.PLATFORM, null, true)));
    assertThatThrownBy(() -> service.delete(2L))
        .isInstanceOf(BaseException.class)
        .satisfies(
            e -> assertThat(((BaseException) e).errorCode()).isEqualTo("SYSTEM_ROLE_READONLY"));
  }

  @Test
  void list_透传仓库排序() {
    Role r1 = new Role("a", "A", Role.Scope.TENANT, null, false);
    Role r2 = new Role("b", "B", Role.Scope.PLATFORM, null, false);
    when(repo.findByOrderByScopeAscIdAsc()).thenReturn(List.of(r1, r2));

    assertThat(service.list()).containsExactly(r1, r2);
  }
}
