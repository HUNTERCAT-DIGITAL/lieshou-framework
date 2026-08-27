package cn.huntercat.lieshou.framework.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

import cn.huntercat.lieshou.framework.common.audit.AuditEvent;
import cn.huntercat.lieshou.framework.common.audit.AuditRecorder;
import cn.huntercat.lieshou.framework.domain.AuditLog;
import cn.huntercat.lieshou.framework.domain.AuditLog.Action;
import cn.huntercat.lieshou.framework.domain.AuditLog.Outcome;
import cn.huntercat.lieshou.framework.domain.AuditLogRepository;

/**
 * 操作审计服务（DATA_SECURITY.md §7 · append-only）.
 *
 * <p>record() 使用 {@code REQUIRES_NEW} 独立事务：即使业务事务回滚，审计也落库 （审计关注「发生了什么尝试」，不随业务成败回滚）。
 *
 * <p>实现 {@link AuditRecorder}（common SPI · L2-1）：使 {@code @Audited} 注解切面在本服务 直接复用 {@link AuditLog}
 * 落库（覆盖默认 LoggingAuditRecorder）。
 */
@Service
public class AuditService implements AuditRecorder {

  private final AuditLogRepository repo;

  public AuditService(AuditLogRepository repo) {
    this.repo = repo;
  }

  /** 审计事件 → AuditLog 落库（@Audited 注解切面入口 · L2-1 接入） */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditEvent event) {
    record(
        event.tenantId(),
        event.userId(),
        mapAction(event.action()),
        event.resource(),
        event.resourceId(),
        event.detail(),
        event.clientIp(),
        event.userAgent(),
        mapOutcome(event.outcome()),
        null);
  }

  /** 动作字符串 → AuditLog.Action（未知动作安全回落 READ，不阻断审计） */
  private static Action mapAction(String action) {
    if (action == null) return Action.READ;
    try {
      return Action.valueOf(action);
    } catch (IllegalArgumentException e) {
      return Action.READ;
    }
  }

  /** 审计事件 outcome → AuditLog.Outcome（FAILURE → ERROR） */
  private static Outcome mapOutcome(AuditEvent.Outcome outcome) {
    return outcome == AuditEvent.Outcome.FAILURE ? Outcome.ERROR : Outcome.SUCCESS;
  }

  /** 从请求提取来源 IP（X-Forwarded-For 优先，回退 remoteAddr）与 UA */
  public static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    String remote = req.getRemoteAddr();
    return remote == null ? null : remote;
  }

  public static String userAgent(HttpServletRequest req) {
    String ua = req.getHeader("User-Agent");
    return (ua == null || ua.isBlank()) ? null : (ua.length() > 255 ? ua.substring(0, 255) : ua);
  }

  /** 记录一次操作（独立事务，业务回滚不影响审计） */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AuditLog record(
      Long tenantId,
      Long userId,
      Action action,
      String resourceType,
      Long resourceId,
      String detail,
      String sourceIp,
      String userAgent,
      Outcome outcome,
      String requestId) {
    AuditLog log = new AuditLog();
    log.setTenantId(tenantId);
    log.setUserId(userId);
    log.setAction(action);
    log.setResourceType(resourceType);
    log.setResourceId(resourceId);
    log.setDetail(truncate(detail, 500));
    log.setSourceIp(sourceIp);
    log.setUserAgent(userAgent);
    log.setOutcome(outcome);
    log.setRequestId(requestId);
    return repo.save(log);
  }

  /** 便捷：记录成功操作（作用域租户 = 请求租户；平台操作用操作者租户兜底） */
  public void recordSuccess(
      Long tenantId,
      Long userId,
      Action action,
      String resourceType,
      Long resourceId,
      String detail,
      HttpServletRequest req) {
    record(
        tenantId,
        userId,
        action,
        resourceType,
        resourceId,
        detail,
        clientIp(req),
        userAgent(req),
        Outcome.SUCCESS,
        req.getHeader("X-Request-Id"));
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
