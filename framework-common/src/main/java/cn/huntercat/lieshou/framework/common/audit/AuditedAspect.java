package cn.huntercat.lieshou.framework.common.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.time.Instant;

/**
 * 审计切面（L2-1 · Bottom-Up）.
 *
 * <p>{@code @Audited} 方法执行后组装 {@link AuditEvent} 并交给 {@link AuditRecorder}： 成功记 {@code
 * SUCCESS}；抛出异常记 {@code FAILURE} 并原样重抛（审计不改变业务语义）。 租户 / 用户 / IP / UA 从请求上下文提取；内部调用（无请求）时相关字段为
 * {@code null}。
 */
@Aspect
@Component
public class AuditedAspect {

  private final ObjectProvider<AuditRecorder> recorderProvider;

  public AuditedAspect(ObjectProvider<AuditRecorder> recorderProvider) {
    this.recorderProvider = recorderProvider;
  }

  @Around("@annotation(audited)")
  public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
    AuditEvent.Context ctx = resolveContext();
    String action = audited.action();
    String resource = audited.resource();
    try {
      Object result = pjp.proceed();
      record(ctx, action, resource, AuditEvent.Outcome.SUCCESS, null);
      return result;
    } catch (Throwable t) {
      record(ctx, action, resource, AuditEvent.Outcome.FAILURE, t.getMessage());
      throw t;
    }
  }

  private void record(
      AuditEvent.Context ctx,
      String action,
      String resource,
      AuditEvent.Outcome outcome,
      String detail) {
    AuditRecorder recorder = recorderProvider.getIfAvailable();
    if (recorder == null) {
      // 无 recorder 时静默降级：审计是横切关注点，不应阻断业务
      return;
    }
    recorder.record(
        new AuditEvent(
            ctx.tenantId(),
            ctx.userId(),
            action,
            resource,
            null,
            outcome,
            detail,
            ctx.clientIp(),
            ctx.userAgent(),
            Instant.now()));
  }

  private static AuditEvent.Context resolveContext() {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return AuditEvent.Context.EMPTY;
    }
    HttpServletRequest req = attrs.getRequest();
    return new AuditEvent.Context(
        parseLong(req.getHeader("X-Tenant-Id")),
        parseLong(req.getHeader("X-User-Id")),
        clientIp(req),
        req.getHeader("User-Agent"));
  }

  /** X-Forwarded-For 优先，回退 remoteAddr（与 user-service AuditService 同逻辑） */
  private static String clientIp(HttpServletRequest req) {
    String xff = req.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return req.getRemoteAddr();
  }

  private static Long parseLong(String v) {
    if (v == null || v.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
