package cn.huntercat.lieshou.framework.common.audit;

/**
 * 审计写入 SPI（L2-1 · Bottom-Up）.
 *
 * <p>底座默认提供 {@link LoggingAuditRecorder}（结构化日志）；需要落库的服务注册 自己的 {@code AuditRecorder} bean 即覆盖默认实现（如
 * user-service 复用现有 {@code AuditLog} 表 / approval-service 复用 {@code ApprovalAuditLog} 表）。
 */
public interface AuditRecorder {

  /** 记录一次操作审计（实现方自行决定同步 / 异步、落库 / 日志） */
  void record(AuditEvent event);
}
