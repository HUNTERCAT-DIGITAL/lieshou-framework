package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 角色 Repository（RBAC · ADR-0024）. */
public interface RoleRepository extends JpaRepository<Role, Long> {

  Optional<Role> findByCode(String code);

  List<Role> findByOrderByScopeAscIdAsc();
}
