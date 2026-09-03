package cn.huntercat.lieshou.framework.rules.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 规则定义（轻量规则引擎 · SpEL 条件 + 动作类型 · ADR-0035）. */
@Entity
@Table(name = "rules")
@Schema(description = "规则定义（SpEL 条件 + 动作）")
public class RuleDefinition {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Schema(description = "主键", accessMode = Schema.AccessMode.READ_ONLY)
  private Long id;

  @Column(name = "tenant_id")
  @Schema(description = "所属租户（null = 全局规则，适用所有租户）")
  private Long tenantId;

  @Column(nullable = false, length = 64)
  @Schema(
      description = "规则编码（租户内唯一）",
      example = "member.dues.overdue",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String code;

  @Column(nullable = false, length = 128)
  @Schema(description = "规则名称", example = "会费逾期催缴", requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @Column(nullable = false, length = 32)
  @Schema(
      description = "规则领域（member/dues/budget/activity/approval...）",
      example = "dues",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String domain;

  @Column(length = 500)
  @Schema(description = "规则描述")
  private String description;

  @Column(length = 2000)
  @Schema(
      description = "SpEL 条件表达式（空 = 恒真），变量为 facts 键名",
      example = "#status == 'DUE' and #daysOverdue > 30")
  private String condition;

  @Column(nullable = false, length = 64)
  @Schema(
      description = "动作类型（由消费方 RuleActionHandler 注册）",
      example = "SEND_MAIL",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String action;

  @Column(name = "action_params", length = 2000)
  @Schema(description = "动作参数（JSON 文本）")
  private String actionParams;

  @Column(nullable = false)
  @Schema(description = "优先级（降序匹配）")
  private int priority = 0;

  @Column(nullable = false)
  @Schema(description = "是否启用")
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Schema(description = "创建时间", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant createdAt;

  @Column(name = "updated_at")
  @Schema(description = "更新时间", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant updatedAt;

  public RuleDefinition() {}

  public RuleDefinition(
      Long tenantId, String code, String name, String domain, String condition, String action) {
    this.tenantId = tenantId;
    this.code = code;
    this.name = name;
    this.domain = domain;
    this.condition = condition;
    this.action = action;
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

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(String domain) {
    this.domain = domain;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getActionParams() {
    return actionParams;
  }

  public void setActionParams(String actionParams) {
    this.actionParams = actionParams;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
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
