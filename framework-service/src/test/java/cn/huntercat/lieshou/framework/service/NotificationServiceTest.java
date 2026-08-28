package cn.huntercat.lieshou.framework.service;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.domain.Notification;
import cn.huntercat.lieshou.framework.domain.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** NotificationService 单测（P0 上收业务 · 唯一源锁定）。 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository repo;
  @InjectMocks private NotificationService service;

  private static Notification notification(Long id, boolean read) {
    return Notification.builder()
        .tenantId(1L)
        .userId(1L)
        .type("SYSTEM")
        .title("通知" + id)
        .content("内容")
        .createdAt(Instant.parse("2026-08-27T00:00:00Z"))
        .readAt(read ? Instant.parse("2026-08-27T01:00:00Z") : null)
        .build();
  }

  @Test
  void list_未读优先且分页() {
    Notification n1 = notification(1L, false);
    Notification n2 = notification(2L, true);
    Notification n3 = notification(3L, false);
    // 排序已下推 SQL：repo 按未读优先 + 新→旧返回分页结果
    when(repo.findUnreadFirst(eq(1L), eq(1L), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(n3, n1), PageRequest.of(0, 2), 3));

    List<Notification> page = service.list(1L, 1L, 0, 2);

    assertThat(page).extracting(Notification::getTitle).containsExactly("通知3", "通知1");
  }

  @Test
  void unreadCount_透传仓库统计() {
    when(repo.countByTenantIdAndUserIdAndReadAtIsNull(1L, 1L)).thenReturn(3L);
    assertThat(service.unreadCount(1L, 1L)).isEqualTo(3L);
  }

  @Test
  void markRead_返回是否命中() {
    when(repo.markRead(eq(7L), eq(1L), eq(1L), any(Instant.class))).thenReturn(1);
    when(repo.markRead(eq(99L), eq(1L), eq(1L), any(Instant.class))).thenReturn(0);

    assertThat(service.markRead(7L, 1L, 1L)).isTrue();
    assertThat(service.markRead(99L, 1L, 1L)).isFalse();
  }

  @Test
  void markAllRead_返回本次条数() {
    when(repo.markAllRead(eq(1L), eq(1L), any(Instant.class))).thenReturn(2);
    assertThat(service.markAllRead(1L, 1L)).isEqualTo(2);
  }

  @Test
  void send_缺省类型为SYSTEM且保存() {
    when(repo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

    Notification n = service.send(1L, 1L, null, "标题", "内容", "biz", 5L);

    assertThat(n.getType()).isEqualTo("SYSTEM");
    assertThat(n.getTitle()).isEqualTo("标题");
    assertThat(n.getContent()).isEqualTo("内容");
    assertThat(n.getBizType()).isEqualTo("biz");
    assertThat(n.getBizId()).isEqualTo(5L);
    verify(repo).save(any(Notification.class));
  }

  @Test
  void find_租户维度查询() {
    when(repo.findByIdAndTenantIdAndUserId(7L, 1L, 1L))
        .thenReturn(Optional.of(notification(7L, false)));
    assertThat(service.find(7L, 1L, 1L)).isPresent();
    assertThat(service.find(7L, 2L, 1L)).isEmpty(); // 跨租户
  }
}
