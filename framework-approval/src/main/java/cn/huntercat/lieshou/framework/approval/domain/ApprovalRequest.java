package cn.huntercat.lieshou.framework.approval.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** 通用审批请求（审批流 · tenant_id 行级隔离 · ADR-0025 模式 · ADR-0032）. */
@Entity
@Table(name = "approval_requests")
@Schema(description = "通用审批请求（approval-service 独占）")
public class ApprovalRequest {

  /** 审批类型 */
  public enum Type {
    /** 支出/报销 */
    EXPENSE,
    /** 采购 */
    PURCHASE,
    /** 销售出库 */
    SALE,
    /** 其他 */
    OTHER
  }

  /** 审批状态（终态不可再流转） */
  public enum Status {
    /** 待审批 */
    PENDING,
    /** 已通过 */
    APPROVED,
    /** 已驳回 */
    REJECTED,
    /** 已撤销 */
    CANCELLED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Schema(description = "Auto-assigned primary key", accessMode = Schema.AccessMode.READ_ONLY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  @Schema(description = "Owning tenant", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long tenantId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Schema(
      description = "EXPENSE 支出/报销 / PURCHASE 采购 / SALE 销售出库 / OTHER 其他",
      example = "EXPENSE",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Type type;

  @Column(nullable = false, length = 128)
  @Schema(description = "审批标题", example = "报销 8 月差旅费", requiredMode = Schema.RequiredMode.REQUIRED)
  private String title;

  @Column(precision = 14, scale = 2)
  @Schema(description = "金额（元，金额类单据填）", example = "1280.00")
  private BigDecimal amount;

  @Column(length = 2000)
  @Schema(description = "详情（JSON 文本或自由描述）")
  private String detail;

  @Column(name = "requester_id", nullable = false)
  @Schema(
      description = "发起人（逻辑 ref -> users.id，来自 X-User-Id）",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Long requesterId;

  @Column(name = "approver_id", nullable = false)
  @Schema(description = "指定审批人（逻辑 ref -> users.id）", requiredMode = Schema.RequiredMode.REQUIRED)
  private Long approverId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Schema(description = "审批状态", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
  private Status status = Status.PENDING;

  @Column(length = 500)
  @Schema(description = "审批意见（驳回必填）/ 撤销原因")
  private String comment;

  @Column(name = "decided_by")
  @Schema(description = "决策人（审批人 或 撤销的发起人）", accessMode = Schema.AccessMode.READ_ONLY)
  private Long decidedBy;

  @Column(name = "decided_at")
  @Schema(description = "决策时间", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant decidedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Schema(description = "Create timestamp", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant createdAt;

  @Column(name = "updated_at")
  @Schema(description = "Update timestamp", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant updatedAt;

  public ApprovalRequest() {}

  public ApprovalRequest(
      Long tenantId,
      Type type,
      String title,
      BigDecimal amount,
      Long requesterId,
      Long approverId) {
    this.tenantId = tenantId;
    this.type = type;
    this.title = title;
    this.amount = amount;
    this.requesterId = requesterId;
    this.approverId = approverId;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public void setAmount(BigDecimal amount) {
    this.amount = amount;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Long getRequesterId() {
    return requesterId;
  }

  public void setRequesterId(Long requesterId) {
    this.requesterId = requesterId;
  }

  public Long getApproverId() {
    return approverId;
  }

  public void setApproverId(Long approverId) {
    this.approverId = approverId;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Long getDecidedBy() {
    return decidedBy;
  }

  public void setDecidedBy(Long decidedBy) {
    this.decidedBy = decidedBy;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public void setDecidedAt(Instant decidedAt) {
    this.decidedAt = decidedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
