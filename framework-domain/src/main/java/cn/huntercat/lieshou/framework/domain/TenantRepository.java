package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 租户 Repository（多租户 · ADR-0022）. */
public interface TenantRepository extends JpaRepository<Tenant, Long> {

  /** 按租户编码查（登录用；code 唯一） */
  Optional<Tenant> findByCode(String code);
}
