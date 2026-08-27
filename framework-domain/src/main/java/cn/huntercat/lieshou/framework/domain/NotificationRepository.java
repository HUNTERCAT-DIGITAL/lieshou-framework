package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 站内通知仓库（租户隔离：所有查询按 tenantId + userId 限定） */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /** 接收者通知列表（新→旧，未读优先；limit 手动分页）。 */
  List<Notification> findByTenantIdAndUserIdOrderByReadAtAscCreatedAtDesc(
      Long tenantId, Long userId);

  /** 某用户某条通知（租户限定，防止跨租户越权读取）。 */
  Optional<Notification> findByIdAndTenantIdAndUserId(Long id, Long tenantId, Long userId);

  /** 未读数量。 */
  long countByTenantIdAndUserIdAndReadAtIsNull(Long tenantId, Long userId);

  /** 全部标记已读（返回受影响行数）。 */
  @Modifying
  @Query(
      "update Notification n set n.readAt = :now where n.tenantId = :tenantId and n.userId = :userId and n.readAt is null")
  int markAllRead(
      @Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("now") Instant now);

  /** 单条标记已读（仅当当前为未读）。 */
  @Modifying
  @Query(
      "update Notification n set n.readAt = :now where n.id = :id and n.tenantId = :tenantId and n.userId = :userId and n.readAt is null")
  int markRead(
      @Param("id") Long id,
      @Param("tenantId") Long tenantId,
      @Param("userId") Long userId,
      @Param("now") Instant now);
}
