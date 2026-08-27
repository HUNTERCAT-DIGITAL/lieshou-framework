package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 通用审批请求 Repository（租户内强制过滤 · ADR-0025 模式 · ADR-0032）. */
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

  /** 租户内全部（可选 type / status 过滤），按提交时间倒序 */
  @Query(
      """
      select a from ApprovalRequest a
      where a.tenantId = :tenantId
        and (:type is null or a.type = :type)
        and (:status is null or a.status = :status)
      order by a.createdAt desc, a.id desc
      """)
  List<ApprovalRequest> findTenantRequests(
      @Param("tenantId") Long tenantId,
      @Param("type") ApprovalRequest.Type type,
      @Param("status") ApprovalRequest.Status status);

  /** 我发起的（requester = X-User-Id） */
  @Query(
      """
      select a from ApprovalRequest a
      where a.tenantId = :tenantId and a.requesterId = :userId
        and (:status is null or a.status = :status)
      order by a.createdAt desc, a.id desc
      """)
  List<ApprovalRequest> findByRequester(
      @Param("tenantId") Long tenantId,
      @Param("userId") Long userId,
      @Param("status") ApprovalRequest.Status status);

  /** 待我审批（approver = X-User-Id 且 PENDING） */
  @Query(
      """
      select a from ApprovalRequest a
      where a.tenantId = :tenantId and a.approverId = :userId and a.status = 'PENDING'
      order by a.createdAt desc, a.id desc
      """)
  List<ApprovalRequest> findInbox(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

  /** 待我审批数（工作台待办角标） */
  long countByTenantIdAndApproverIdAndStatus(
      Long tenantId, Long approverId, ApprovalRequest.Status status);

  /** 我发起的待处理数（含 PENDING + REJECTED 可再次发起语义，一期仅 PENDING） */
  long countByTenantIdAndRequesterIdAndStatus(
      Long tenantId, Long requesterId, ApprovalRequest.Status status);
}
