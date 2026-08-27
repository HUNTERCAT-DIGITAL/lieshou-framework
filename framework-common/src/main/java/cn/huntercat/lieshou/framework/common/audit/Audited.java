package cn.huntercat.lieshou.framework.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作审计注解（L2-1 · Bottom-Up）.
 *
 * <p>标注在需要审计的 Controller 方法上，由 {@link AuditedAspect} 在方法执行后 组装 {@link AuditEvent} 交给 {@link
 * AuditRecorder}。动作语义与现有审计体系对齐 （user-service {@code AuditLog.Action} / approval-service 审批审计）。
 *
 * <pre>{@code
 * @Audited(action = "APPROVE", resource = "approval")
 * public ResponseEntity<Void> approve(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

  /** 动作（CREATE / UPDATE / DELETE / APPROVE / REJECT / LOGIN …） */
  String action();

  /** 资源类型（approval / user / customer …） */
  String resource();
}
