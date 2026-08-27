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
 * 角色实体（RBAC · ADR-0024）.
 *
 * <p>两级 scope：PLATFORM（平台运营）/ TENANT（租户内）。系统内置角色（PLATFORM_ADMIN / TENANT_ADMIN / USER）不可删除。
 */
@Entity
@Table(name = "roles")
@Schema(description = "Role definition (RBAC) owned by user-service")
public class Role {

  public enum Scope {
    PLATFORM,
    TENANT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 32)
  private String code;

  @Column(nullable = false, length = 64)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Scope scope = Scope.TENANT;

  @Column(length = 255)
  private String description;

  @Column(name = "is_system", nullable = false)
  private boolean system = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Role() {}

  public Role(String code, String name, Scope scope, String description, boolean system) {
    this.code = code;
    this.name = name;
    this.scope = scope;
    this.description = description;
    this.system = system;
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

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Scope getScope() {
    return scope;
  }

  public void setScope(Scope scope) {
    this.scope = scope;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public boolean isSystem() {
    return system;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
