package cn.huntercat.lieshou.framework.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.common.audit.AuditEvent;
import cn.huntercat.lieshou.framework.domain.AuditLog;
import cn.huntercat.lieshou.framework.domain.AuditLogRepository;

/** AuditService 单测（审计落库 · IP/UA 解析 · 动作/结果映射）。 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

  @Mock private AuditLogRepository repo;
  @InjectMocks private AuditService service;

  @Test
  void record_映射动作与结果并截断详情() {
    when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

    AuditLog log =
        service.record(
            1L,
            2L,
            AuditLog.Action.CREATE,
            "USER",
            3L,
            "d".repeat(600),
            "1.2.3.4",
            "UA",
            AuditLog.Outcome.SUCCESS,
            "req-1");

    assertThat(log.getTenantId()).isEqualTo(1L);
    assertThat(log.getUserId()).isEqualTo(2L);
    assertThat(log.getDetail()).hasSize(500); // 截断
    assertThat(log.getSourceIp()).isEqualTo("1.2.3.4");
    assertThat(log.getRequestId()).isEqualTo("req-1");
    verify(repo).save(any(AuditLog.class));
  }

  @Test
  void recordEvent_未知动作安全回落READ_failure映射ERROR() {
    when(repo.save(any(AuditLog.class))).thenAnswer(inv -> inv.getArgument(0));

    service.record(
        new AuditEvent(
            1L,
            2L,
            "UNKNOWN_ACTION",
            "TENANT",
            9L,
            AuditEvent.Outcome.FAILURE,
            "x",
            "ip",
            "ua",
            null));

    org.mockito.ArgumentCaptor<AuditLog> captor =
        org.mockito.ArgumentCaptor.forClass(AuditLog.class);
    verify(repo).save(captor.capture());
    assertThat(captor.getValue().getAction()).isEqualTo(AuditLog.Action.READ);
    assertThat(captor.getValue().getOutcome()).isEqualTo(AuditLog.Outcome.ERROR);
  }

  @Test
  void clientIp_优先XFF首段() {
    jakarta.servlet.http.HttpServletRequest req =
        new org.springframework.mock.web.MockHttpServletRequest() {
          @Override
          public String getHeader(String name) {
            return "X-Forwarded-For".equals(name) ? "203.0.113.7, 10.0.0.1" : null;
          }
        };
    assertThat(AuditService.clientIp(req)).isEqualTo("203.0.113.7");

    org.springframework.mock.web.MockHttpServletRequest plain =
        new org.springframework.mock.web.MockHttpServletRequest();
    plain.setRemoteAddr("192.168.1.1");
    assertThat(AuditService.clientIp(plain)).isEqualTo("192.168.1.1");
  }

  @Test
  void userAgent_截断超长() {
    jakarta.servlet.http.HttpServletRequest req =
        new org.springframework.mock.web.MockHttpServletRequest() {
          @Override
          public String getHeader(String name) {
            return "User-Agent".equals(name) ? "a".repeat(300) : null;
          }
        };
    assertThat(AuditService.userAgent(req)).hasSize(255);
  }
}
