package cn.huntercat.lieshou.framework.rules.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 规则定义仓储（tenant_id 可空 = 全局规则 · ADR-0035）. */
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Long> {

  List<RuleDefinition> findByTenantIdAndDomainAndEnabledTrueOrderByPriorityDesc(
      Long tenantId, String domain);

  List<RuleDefinition> findByTenantIdIsNullAndDomainAndEnabledTrueOrderByPriorityDesc(
      String domain);

  List<RuleDefinition> findByTenantIdOrderByPriorityDesc(Long tenantId);

  List<RuleDefinition> findByTenantIdIsNullOrderByPriorityDesc();

  Optional<RuleDefinition> findByTenantIdAndCode(Long tenantId, String code);
}
