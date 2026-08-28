package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** 站内通知仓库（租户隔离：所有查询按 tenantId + userId 限定） */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /**
   * 接收者通知列表：未读优先（read_at IS NULL 在前），组内按创建时间新→旧；数据库分页。
   *
   * <p>排序与分页一次下推 SQL（避免全量拉取后内存排序/分页）。
   */
  @Query(
      """
      select n from Notification n
      where n.tenantId = :tenantId and n.userId = :userId
      order by case when n.readAt is null then 0 else 1 end, n.createdAt desc
      """)
  Page<Notification> findUnreadFirst(
      @Param("tenantId") Long tenantId, @Param("userId") Long userId, Pageable pageable);

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
