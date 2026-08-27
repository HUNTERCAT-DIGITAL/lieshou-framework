package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 邀请码 Repository（ADR-0023 Phase 2）. */
public interface TenantInviteRepository extends JpaRepository<TenantInvite, Long> {

  /** 按邀请码查（唯一） */
  Optional<TenantInvite> findByCode(String code);

  /** 某租户的邀请码列表（新→旧） */
  List<TenantInvite> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
