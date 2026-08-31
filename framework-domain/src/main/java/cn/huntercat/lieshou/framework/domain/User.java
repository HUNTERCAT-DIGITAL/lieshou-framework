package cn.huntercat.lieshou.framework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 用户实体（user-service 私有 schema）.
 *
 * <p>Phase 3 起 user-service 独占本实体的所有权，其他服务若要访问只能通过 user-service 暴露的 REST API 或 Feign client.
 *
 * <p>Phase 5 起:
 *
 * <ul>
 *   <li>{@link Schema} 注解让 SpringDoc 把本实体映射成 OpenAPI schema (User).
 *   <li>{@code passwordHash} 字段 —— BCrypt 哈希；仅 {@code /api/users/auth/by-username/**}
 *       端点会返回（service-to-service）. 其他公开端点改用 {@code UserView} DTO，不暴露密码.
 * </ul>
 *
 * <p>Phase 6（ADR-0021）: schema 由 Flyway 管理（{@code V1__init_user_schema.sql}），Hibernate 只 validate.
 * 新增字段：{@code email} / {@code phone} / {@code status} / {@code roles} / {@code updatedAt} / {@code
 * lastLoginAt}.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_users_tenant_username",
          columnNames = {"tenant_id", "username"})
    })
@Schema(description = "User entity owned by user-service")
public class User {

  /** 账户状态（与 {@code users.status} CHECK 约束保持一致） */
  public enum Status {
    ACTIVE,
    DISABLED,
    LOCKED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Schema(
      description = "Auto-assigned primary key",
      example = "1",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Long id;

  @Column(name = "tenant_id", nullable = false)
  @Schema(
      description = "Owning tenant (FK -> tenants.id); mandatory since ADR-0022 multitenancy",
      example = "1",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Long tenantId;

  @Column(nullable = false, length = 64)
  @Schema(
      description = "Login username (unique within tenant)",
      example = "futurewl",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String username;

  @Column(name = "display_name", nullable = false, length = 128)
  @Schema(
      description = "Human display name",
      example = "Future Wang",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String displayName;

  @Column(length = 254)
  @Schema(
      description = "Email (optional, unique; NULL excluded from unique constraint)",
      example = "future@huntercat.cn")
  private String email;

  @Column(length = 20)
  @Schema(description = "Phone (optional)", example = "13800000000")
  private String phone;

  @Column(name = "password_hash", nullable = true, length = 100)  // nullable=true: 管理员建用户可不设密码(首次验证码激活设置)
  @Schema(
      description =
          "BCrypt hash; NEVER exposed via public endpoints. Use /api/users/auth/by-username for service-to-service.",
      accessMode = Schema.AccessMode.WRITE_ONLY)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  @Schema(
      description = "Account status",
      example = "ACTIVE",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private Status status = Status.ACTIVE;

  // RBAC（ADR-0024）：roles 拆表（user_roles 关联）；JSON 序列化为 code 数组（@JsonGetter）
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @com.fasterxml.jackson.annotation.JsonIgnore
  private java.util.List<Role> roles = new java.util.ArrayList<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  @Schema(
      description = "Create timestamp (server-assigned)",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @Schema(
      description = "Last update timestamp (server-assigned)",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Instant updatedAt;

  @Column(name = "last_login_at")
  @Schema(
      description = "Last successful login timestamp (auth-service writes back)",
      accessMode = Schema.AccessMode.READ_ONLY)
  private Instant lastLoginAt;

  public User() {}

  public User(Long tenantId, String username, String displayName, String passwordHash) {
    this.tenantId = tenantId;
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
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

  public void setId(Long id) {
    this.id = id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  /**
   * Phase 6: WRITE_ONLY —— 公开端点序列化 User 时绝不输出 passwordHash.
   *
   * <p>auth-service 走 {@code /api/users/auth/by-username/**} 返回的 {@link UserAuthView} DTO（显式含
   * passwordHash），不受影响。
   */
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  /** RBAC: 角色关联（写路径用）. JSON 序列化走 {@link #getRoleCodes()}（string[] 兼容前端）。 */
  public java.util.List<Role> getRoles() {
    return roles;
  }

  public void setRoles(java.util.List<Role> roles) {
    this.roles = roles;
  }

  /** JSON 输出 roles 为 code 数组（保持 Phase 5+ 前端 string[] 兼容） */
  @com.fasterxml.jackson.annotation.JsonGetter("roles")
  public java.util.List<String> getRoleCodes() {
    return roles.stream().map(Role::getCode).toList();
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

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }
}
