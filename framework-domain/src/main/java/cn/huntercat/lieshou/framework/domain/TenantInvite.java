package cn.huntercat.lieshou.framework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 邀请码实体（ADR-0023 Phase 2）.
 *
 * <p>租户管理员生成 → 受邀人注册填邀请码 → 自动加入该租户并分配角色。
 */
@Entity
@Table(name = "tenant_invites")
@Schema(description = "Tenant invite code (registration auto-join) owned by user-service")
public class TenantInvite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(nullable = false, unique = true, length = 32)
  private String code;

  @Column(nullable = false, length = 16)
  private String role = "USER";

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "max_uses")
  private Integer maxUses;

  @Column(name = "used_count", nullable = false)
  private Integer usedCount = 0;

  @Column(name = "created_by")
  private Long createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  public TenantInvite() {}

  public TenantInvite(
      Long tenantId, String code, String role, Instant expiresAt, Integer maxUses, Long createdBy) {
    this.tenantId = tenantId;
    this.code = code;
    this.role = role;
    this.expiresAt = expiresAt;
    this.maxUses = maxUses;
    this.createdBy = createdBy;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }

  /** 邀请码是否可用：未撤销 && 未过期 && 未用完 */
  public boolean isValid() {
    if (revokedAt != null) return false;
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
    if (maxUses != null && usedCount >= maxUses) return false;
    return true;
  }

  /** 使用一次（调用方需保证 isValid） */
  public void consume() {
    this.usedCount = (usedCount == null ? 0 : usedCount) + 1;
  }

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public String getCode() {
    return code;
  }

  public String getRole() {
    return role;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Integer getMaxUses() {
    return maxUses;
  }

  public Integer getUsedCount() {
    return usedCount;
  }

  public Long getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }
}
