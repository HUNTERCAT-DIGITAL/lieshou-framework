package cn.huntercat.lieshou.framework.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 站内通知（开源版消息通知模块）.
 *
 * <p>平台/业务事件推送给租户内用户：{@code tenant_id + user_id} 定位接收者（租户隔离）， {@code read_at} 为空表示未读；{@code
 * biz_type / biz_id} 预留业务关联（审批/审计等扩展）。
 */
@Entity
@Table(name = "notifications")
@Schema(description = "In-app notification (tenant-scoped)")
public class Notification {

  /** 通知类型：SYSTEM / APPROVAL / AUDIT 等（预留业务扩展）。 */
  public enum Type {
    SYSTEM,
    APPROVAL,
    AUDIT
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "tenant_id")
  private Long tenantId;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "type")
  private String type;

  @Column(name = "title")
  private String title;

  @Column(name = "content")
  private String content;

  @Column(name = "biz_type")
  private String bizType;

  @Column(name = "biz_id")
  private Long bizId;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at")
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public Long getTenantId() {
    return tenantId;
  }

  public Long getUserId() {
    return userId;
  }

  public String getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getContent() {
    return content;
  }

  public String getBizType() {
    return bizType;
  }

  public Long getBizId() {
    return bizId;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** 通知构造器（发送端使用）。 */
  public static class Builder {
    private final Notification n = new Notification();

    public Builder tenantId(Long tenantId) {
      n.tenantId = tenantId;
      return this;
    }

    public Builder userId(Long userId) {
      n.userId = userId;
      return this;
    }

    public Builder type(String type) {
      n.type = type;
      return this;
    }

    public Builder title(String title) {
      n.title = title;
      return this;
    }

    public Builder content(String content) {
      n.content = content;
      return this;
    }

    public Builder bizType(String bizType) {
      n.bizType = bizType;
      return this;
    }

    public Builder bizId(Long bizId) {
      n.bizId = bizId;
      return this;
    }

    public Builder readAt(Instant readAt) {
      n.readAt = readAt;
      return this;
    }

    public Builder createdAt(Instant createdAt) {
      n.createdAt = createdAt;
      return this;
    }

    public Notification build() {
      return n;
    }
  }
}
