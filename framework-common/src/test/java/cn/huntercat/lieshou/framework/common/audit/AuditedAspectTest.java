package cn.huntercat.lieshou.framework.common.audit;

import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.huntercat.lieshou.framework.common.audit.AuditEvent.Outcome;
import java.util.ArrayList;
import java.util.List;

/** AuditedAspect 单测（L2-1 · 审计注解收尾）——AspectJ 手动代理，不依赖 Spring 上下文. */
class AuditedAspectTest {

  /** 测试用的审计目标：方法级 @Audited */
  static class Target {
    @Audited(action = "APPROVE", resource = "approval")
    public String approve() {
      return "ok";
    }

    @Audited(action = "DELETE", resource = "customer")
    public void delete() {
      throw new IllegalStateException("boom");
    }
  }

  private final List<AuditEvent> events = new ArrayList<>();

  private Target proxy() {
    AuditedAspect aspect = new AuditedAspect(provider(events));
    AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
    factory.addAspect(aspect);
    return factory.getProxy();
  }

  @Test
  void success_recordsOutcomeAndReturnsResult() {
    String result = proxy().approve();

    assertThat(result).isEqualTo("ok");
    assertThat(events).hasSize(1);
    AuditEvent e = events.get(0);
    assertThat(e.action()).isEqualTo("APPROVE");
    assertThat(e.resource()).isEqualTo("approval");
    assertThat(e.outcome()).isEqualTo(Outcome.SUCCESS);
  }

  @Test
  void failure_recordsFailureAndRethrows() {
    assertThatThrownBy(proxy()::delete)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");

    assertThat(events).hasSize(1);
    AuditEvent e = events.get(0);
    assertThat(e.action()).isEqualTo("DELETE");
    assertThat(e.outcome()).isEqualTo(Outcome.FAILURE);
    assertThat(e.detail()).isEqualTo("boom");
  }

  @Test
  void tenantAndUserExtractedFromHeaders() {
    Target p = proxy();

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Tenant-Id", "7");
    req.addHeader("X-User-Id", "42");
    req.setRemoteAddr("10.0.0.9");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    try {
      p.approve();
    } finally {
      RequestContextHolder.resetRequestAttributes();
    }

    AuditEvent e = events.get(0);
    assertThat(e.tenantId()).isEqualTo(7L);
    assertThat(e.userId()).isEqualTo(42L);
    assertThat(e.clientIp()).isEqualTo("10.0.0.9");
  }

  @Test
  void contextFieldsNullWithoutRequest() {
    Target p = proxy();
    p.approve();

    AuditEvent e = events.get(0);
    assertThat(e.tenantId()).isNull();
    assertThat(e.userId()).isNull();
  }

  /** ObjectProvider 包装收集器 recorder（替代默认 LoggingAuditRecorder） */
  private static ObjectProvider<AuditRecorder> provider(List<AuditEvent> sink) {
    StaticListableBeanFactory bf = new StaticListableBeanFactory();
    bf.addBean("recorder", (AuditRecorder) sink::add);
    return bf.getBeanProvider(AuditRecorder.class);
  }
}
