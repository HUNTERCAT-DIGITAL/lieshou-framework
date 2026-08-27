package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 审批审计仓库（append-only · DATA_SECURITY.md §7）.
 *
 * <p>只暴露 save + 查询；无 update/delete 路径（审计不可篡改）。
 */
public interface ApprovalAuditLogRepository extends JpaRepository<ApprovalAuditLog, Long> {

  /** 租户内审计列表（新 → 旧） */
  List<ApprovalAuditLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
