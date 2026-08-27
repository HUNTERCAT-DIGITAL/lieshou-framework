package cn.huntercat.lieshou.framework.common.web;

import org.springframework.mock.web.MockHttpServletRequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TenantHeaders 工具单测（Bottom-Up 抽象）. */
class TenantHeadersTest {

  @Test
  void parsesTenantId() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-Tenant-Id", "7");
    assertThat(TenantHeaders.tenantId(req)).isEqualTo(7L);
  }

  @Test
  void missingOrInvalidTenantId_returnsNull() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    assertThat(TenantHeaders.tenantId(req)).isNull();
    req.addHeader("X-Tenant-Id", "abc");
    assertThat(TenantHeaders.tenantId(req)).isNull();
  }

  @Test
  void parsesUserId() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("X-User-Id", "42");
    assertThat(TenantHeaders.userId(req)).isEqualTo(42L);
  }

  @Test
  void parseLong_trimsAndHandlesBlank() {
    assertThat(TenantHeaders.parseLong(" 99 ")).isEqualTo(99L);
    assertThat(TenantHeaders.parseLong("")).isNull();
    assertThat(TenantHeaders.parseLong(null)).isNull();
    assertThat(TenantHeaders.parseLong("x")).isNull();
  }
}
