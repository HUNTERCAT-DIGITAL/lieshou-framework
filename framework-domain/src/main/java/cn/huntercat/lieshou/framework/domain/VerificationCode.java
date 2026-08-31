package cn.huntercat.lieshou.framework.domain;

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
import java.time.Instant;

/**
 * 验证码实体（认证体系扩展 · ADR-0023）.
 *
 * <p>短信 / 邮箱验证码，一次性（usedAt 标记），用于登录 / 注册 / 重置密码。
 */
@Entity
@Table(name = "verification_codes")
@Schema(description = "One-time verification code (SMS/EMAIL) owned by user-service")
public class VerificationCode {

  public enum Channel {
    SMS,
    EMAIL
  }

  public enum Purpose {
    LOGIN,
    REGISTER,
    RESET_PASSWORD,
    /** 首次登录激活(未设置密码的用户用验证码激活并设置密码 · 2026-08) */
    ACTIVATE
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Channel channel;

  @Column(nullable = false, length = 254)
  private String target;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Purpose purpose;

  @Column(nullable = false, length = 10)
  private String code;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public VerificationCode() {}

  public VerificationCode(
      Channel channel, String target, Purpose purpose, String code, Instant expiresAt) {
    this.channel = channel;
    this.target = target;
    this.purpose = purpose;
    this.code = code;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) createdAt = Instant.now();
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public Long getId() {
    return id;
  }

  public Channel getChannel() {
    return channel;
  }

  public String getTarget() {
    return target;
  }

  public Purpose getPurpose() {
    return purpose;
  }

  public String getCode() {
    return code;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public void setUsedAt(Instant usedAt) {
    this.usedAt = usedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
