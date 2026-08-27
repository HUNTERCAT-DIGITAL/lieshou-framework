package cn.huntercat.lieshou.framework.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 审批操作审计（ADR-0032 阶段 2 · DATA_SECURITY.md §7 · append-only）.
 *
 * <p>六要素：who(actor_id) / when(created_at) / what(action+resource_id) / from(source_ip+user_agent) /
 * outcome / request_id。仓库只暴露 save + 查询，无 update/delete。
 */
@Entity
@Table(name = "approval_audit_logs")
public class ApprovalAuditLog {

  public enum Action {
    CREATE,
    APPROVE,
    REJECT,
    CANCEL
  }

  public enum Outcome {
    SUCCESS,
    FAILURE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private Action action;

  @Column(name = "resource_id")
  private Long resourceId;

  @Column(name = "actor_id")
  private Long actorId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Outcome outcome;

  @Column(length = 1000)
  private String detail;

  @Column(name = "source_ip", length = 64)
  private String sourceIp;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "request_id", length = 64)
  private String requestId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Action getAction() {
    return action;
  }

  public void setAction(Action action) {
    this.action = action;
  }

  public Long getResourceId() {
    return resourceId;
  }

  public void setResourceId(Long resourceId) {
    this.resourceId = resourceId;
  }

  public Long getActorId() {
    return actorId;
  }

  public void setActorId(Long actorId) {
    this.actorId = actorId;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public void setOutcome(Outcome outcome) {
    this.outcome = outcome;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public String getSourceIp() {
    return sourceIp;
  }

  public void setSourceIp(String sourceIp) {
    this.sourceIp = sourceIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
