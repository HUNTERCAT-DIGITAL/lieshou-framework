package cn.huntercat.lieshou.framework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 租户实体（多租户 · ADR-0022）.
 *
 * <p>共享表 + tenant_id 行级隔离：所有业务实体归属某个租户，查询强制带 tenant 维度。 登录用 {@code code} 标识租户（如 huntercat / zhiye）。
 */
@Entity
@Table(name = "tenants")
@Schema(description = "Tenant (enterprise/organization) owned by user-service")
public class Tenant {

  /** 租户状态（与 tenants.status CHECK 对齐） */
  public enum Status {
    ACTIVE,
    DISABLED
  }

  /**
   * 租户版别（与 tenants.edition CHECK 对齐 · ADR-0035 客户项目模型）.
   *
   * <p>版别 = 行业版 / 客户版标识，驱动前端门户/登录的品牌与功能开关； 同一套代码按 edition 渲染不同门户（客户差异进配置层，禁止 fork 仓库）。
   */
  public enum Edition {
    /** 通用版（猎手云 Pro 默认，如 huntercat） */
    GENERIC,
    /** 法律行业版（律所/事务所） */
    LAYER,
    /** LegalMind Unity 版（凌科安时联合定制 · 律师成长操作系统 · ADR-0036） */
    LEGALMIND,
    /** 教育行业版（教育机构） */
    ZHIYE,
    /** 精密制造版（制造企业） */
    JMZZ
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Schema(
      description = "Auto-assigned primary key",
      example = "1",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Long id;

  @Column(nullable = false, length = 128)
  @Schema(
      description = "Enterprise display name",
      example = "南昌猎手猫数字科技有限公司",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String name;

  @Column(nullable = false, unique = true, length = 64)
  @Schema(
      description = "Tenant code used for login (unique)",
      example = "huntercat",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Schema(
      description = "Tenant status",
      example = "ACTIVE",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Status status = Status.ACTIVE;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  @Schema(
      description =
          "Edition (industry/customer edition): GENERIC | LAYER | LEGALMIND | ZHIYE | JMZZ",
      example = "GENERIC",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Edition edition = Edition.GENERIC;

  @Column(name = "created_at", nullable = false, updatable = false)
  @Schema(description = "Create timestamp", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @Schema(description = "Last update timestamp", accessMode = Schema.AccessMode.READ_ONLY)
  private Instant updatedAt;

  public Tenant() {}

  public Tenant(String name, String code) {
    this.name = name;
    this.code = code;
  }

  public Tenant(String name, String code, Edition edition) {
    this.name = name;
    this.code = code;
    this.edition = edition == null ? Edition.GENERIC : edition;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Edition getEdition() {
    return edition;
  }

  public void setEdition(Edition edition) {
    this.edition = edition == null ? Edition.GENERIC : edition;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
