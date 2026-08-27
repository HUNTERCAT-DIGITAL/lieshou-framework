package cn.huntercat.lieshou.framework.approval;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.domain.ApprovalAuditLog;
import cn.huntercat.lieshou.framework.domain.ApprovalAuditLog.Action;
import cn.huntercat.lieshou.framework.domain.ApprovalAuditLog.Outcome;
import cn.huntercat.lieshou.framework.domain.ApprovalAuditLogRepository;

/**
 * 审批操作审计服务（ADR-0032 阶段 2 · DATA_SECURITY.md §7 · append-only）.
 *
 * <p>REQUIRES_NEW 独立事务：业务回滚不影响审计落库；复用 ADR-0030 的 AuditService 模式。
 */
@Service
public class ApprovalAuditService {

  private final ApprovalAuditLogRepository repo;

  public ApprovalAuditService(ApprovalAuditLogRepository repo) {
    this.repo = repo;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ApprovalAuditLog record(
      Long tenantId,
      Long actorId,
      Action action,
      Long resourceId,
      String detail,
      String sourceIp,
      String userAgent,
      Outcome outcome,
      String requestId) {
    ApprovalAuditLog log = new ApprovalAuditLog();
    log.setTenantId(tenantId);
    log.setActorId(actorId);
    log.setAction(action);
    log.setResourceId(resourceId);
    log.setDetail(truncate(detail, 1000));
    log.setSourceIp(truncate(sourceIp, 64));
    log.setUserAgent(truncate(userAgent, 512));
    log.setOutcome(outcome);
    log.setRequestId(truncate(requestId, 64));
    return repo.save(log);
  }

  /** 成功操作（带请求上下文） */
  public void recordSuccess(
      Long tenantId,
      Long actorId,
      Action action,
      Long resourceId,
      String detail,
      String sourceIp,
      String userAgent,
      String requestId) {
    record(
        tenantId,
        actorId,
        action,
        resourceId,
        detail,
        sourceIp,
        userAgent,
        Outcome.SUCCESS,
        requestId);
  }

  /** 简化调用：无请求上下文（Feign 业务挂接等内部调用） */
  public void recordSuccess(
      Long tenantId, Long actorId, Action action, Long resourceId, String detail) {
    record(tenantId, actorId, action, resourceId, detail, null, null, Outcome.SUCCESS, null);
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
