package cn.huntercat.lieshou.framework.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.domain.Notification;
import cn.huntercat.lieshou.framework.domain.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 站内通知服务（开源版消息通知模块 · 三套产品线共用）.
 *
 * <p>实体/仓库来自 framework-domain（单一事实源）；接收者维度（tenantId + userId）读写， 发送端（平台管理）经 {@link #send} 创建。消费方（单体
 * backend / user-service 薄壳） 仅保留 Controller 装配，不再各自维护实现。
 */
@Service
public class NotificationService {

  private static final int MAX_PAGE_SIZE = 100;

  private final NotificationRepository repo;

  public NotificationService(NotificationRepository repo) {
    this.repo = repo;
  }

  /** 接收者通知列表（未读优先，新→旧，数据库分页）。 */
  @Transactional(readOnly = true)
  public List<Notification> list(Long tenantId, Long userId, int page, int size) {
    int cap = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Pageable pageable = PageRequest.of(Math.max(page, 0), cap);
    return repo.findUnreadFirst(tenantId, userId, pageable).getContent();
  }

  /** 未读数量。 */
  @Transactional(readOnly = true)
  public long unreadCount(Long tenantId, Long userId) {
    return repo.countByTenantIdAndUserIdAndReadAtIsNull(tenantId, userId);
  }

  /** 标记单条已读；不存在/已读返回 false。 */
  @Transactional
  public boolean markRead(Long id, Long tenantId, Long userId) {
    return repo.markRead(id, tenantId, userId, Instant.now()) > 0;
  }

  /** 全部标记已读；返回本次新标记条数。 */
  @Transactional
  public int markAllRead(Long tenantId, Long userId) {
    return repo.markAllRead(tenantId, userId, Instant.now());
  }

  /** 发送通知（平台管理/业务事件触发）。 */
  @Transactional
  public Notification send(
      Long tenantId,
      Long userId,
      String type,
      String title,
      String content,
      String bizType,
      Long bizId) {
    Notification n =
        Notification.builder()
            .tenantId(tenantId)
            .userId(userId)
            .type(type == null || type.isBlank() ? Notification.Type.SYSTEM.name() : type)
            .title(title)
            .content(content == null ? "" : content)
            .bizType(bizType)
            .bizId(bizId)
            .createdAt(Instant.now())
            .build();
    return repo.save(n);
  }

  /** 校验单条存在性（供 controller 区分 404）。 */
  @Transactional(readOnly = true)
  public Optional<Notification> find(Long id, Long tenantId, Long userId) {
    return repo.findByIdAndTenantIdAndUserId(id, tenantId, userId);
  }
}
