package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 操作审计日志仓库（append-only：只有 save + 查询，无 update/delete 方法） */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  List<AuditLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

  long countByTenantId(Long tenantId);
}
