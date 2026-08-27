package cn.huntercat.lieshou.framework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 操作审计日志（append-only · DATA_SECURITY.md §7）.
 *
 * <p>六要素：who（userId）/ when（createdAt）/ what（action + resourceType + resourceId + detail）/
 * from（sourceIp + userAgent）/ outcome / requestId。只新增不修改不删除。
 */
@Entity
@Table(name = "audit_logs")
@Schema(description = "Operation audit log (append-only)")
public class AuditLog {

  public enum Action {
    CREATE,
    UPDATE,
    DELETE,
    DENIED,
    LOGIN,
    READ
  }

  public enum Outcome {
    SUCCESS,
    DENIED,
    ERROR
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "user_id")
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Action action;

  @Column(name = "resource_type", nullable = false, length = 32)
  private String resourceType;

  @Column(name = "resource_id")
  private Long resourceId;

  @Column(length = 500)
  private String detail;

  @Column(name = "source_ip", length = 64)
  private String sourceIp;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Outcome outcome = Outcome.SUCCESS;

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

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public Action getAction() {
    return action;
  }

  public void setAction(Action action) {
    this.action = action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public Long getResourceId() {
    return resourceId;
  }

  public void setResourceId(Long resourceId) {
    this.resourceId = resourceId;
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

  public Outcome getOutcome() {
    return outcome;
  }

  public void setOutcome(Outcome outcome) {
    this.outcome = outcome;
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
