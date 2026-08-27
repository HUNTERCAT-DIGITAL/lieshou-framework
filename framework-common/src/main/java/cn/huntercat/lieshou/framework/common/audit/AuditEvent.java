package cn.huntercat.lieshou.framework.common.audit;

import java.time.Instant;

/**
 * 审计事件（L2-1 · Bottom-Up）.
 *
 * <p>由 {@link AuditedAspect} 在 {@code @Audited} 方法执行后组装，交由 {@link AuditRecorder} 落库 /
 * 结构化输出。租户与用户来自请求头 （gateway 从 JWT 注入 {@code X-Tenant-Id} / {@code X-User-Id}）， 内部调用（无请求上下文）时为
 * {@code null}。
 */
public record AuditEvent(
    Long tenantId,
    Long userId,
    String action,
    String resource,
    Long resourceId,
    Outcome outcome,
    String detail,
    String clientIp,
    String userAgent,
    Instant occurredAt) {

  /** 操作结果：业务事务成败不影响审计记录（关注"发生了什么尝试"） */
  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  /** 请求上下文快照（切面从 HttpServletRequest 提取；内部调用为 null） */
  public record Context(Long tenantId, Long userId, String clientIp, String userAgent) {

    public static final Context EMPTY = new Context(null, null, null, null);
  }
}
