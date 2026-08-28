package cn.huntercat.lieshou.framework.common.audit;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger log = LoggerFactory.getLogger(AuditedAspect.class);

  private static final SpelExpressionParser SPEL = new SpelExpressionParser();
  private static final DefaultParameterNameDiscoverer PARAM_NAMES =
      new DefaultParameterNameDiscoverer();

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
      record(
          ctx, action, resource, resolveResourceId(pjp, audited), AuditEvent.Outcome.SUCCESS, null);
      return result;
    } catch (Throwable t) {
      record(
          ctx,
          action,
          resource,
          resolveResourceId(pjp, audited),
          AuditEvent.Outcome.FAILURE,
          t.getMessage());
      throw t;
    }
  }

  /**
   * 解析资源 ID：注解 {@code resourceId} SpEL 优先（变量绑定方法参数名 + {@code p0..pn}）； 未指定/解析失败 → 回退取第一个数值参数；仍无 →
   * null。
   */
  private static Long resolveResourceId(ProceedingJoinPoint pjp, Audited audited) {
    Object[] args = pjp.getArgs();
    String expr = audited.resourceId();
    if (expr != null && !expr.isBlank()) {
      try {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        String[] names = parameterNames(pjp);
        for (int i = 0; i < args.length; i++) {
          ctx.setVariable("p" + i, args[i]);
          if (names != null && i < names.length && names[i] != null) {
            ctx.setVariable(names[i], args[i]);
          }
        }
        return toLong(SPEL.parseExpression(expr).getValue(ctx));
      } catch (RuntimeException e) {
        log.debug("Audited resourceId SpEL 解析失败 expr={}，回退首个数值参数", expr, e);
      }
    }
    for (Object arg : args) {
      if (arg instanceof Number n) {
        return n.longValue();
      }
    }
    return null;
  }

  private static String[] parameterNames(ProceedingJoinPoint pjp) {
    if (pjp.getSignature() instanceof MethodSignature ms) {
      return PARAM_NAMES.getParameterNames(ms.getMethod());
    }
    return null;
  }

  private static Long toLong(Object value) {
    if (value instanceof Number n) {
      return n.longValue();
    }
    if (value instanceof String s) {
      try {
        return Long.parseLong(s.trim());
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private void record(
      AuditEvent.Context ctx,
      String action,
      String resource,
      Long resourceId,
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
            resourceId,
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
