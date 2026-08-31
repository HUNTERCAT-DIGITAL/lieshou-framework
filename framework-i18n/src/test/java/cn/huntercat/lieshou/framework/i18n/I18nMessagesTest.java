package cn.huntercat.lieshou.framework.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 共享多语言单测（L0 · S5）.
 *
 * <p>验证：zh/en 本地化、参数插值、未知 key fallback（返回 key 本身）。
 * 数据源由 lieshou-i18n 生成器产出（framework-i18n/src/main/resources/i18n/messages_*.properties）。
 */
class I18nMessagesTest {

  private I18nMessages i18n;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
    ms.setBasename("i18n/messages");
    ms.setDefaultEncoding("UTF-8");
    ms.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    ms.setFallbackToSystemLocale(false);
    i18n = new I18nMessages();
    i18n.setMessageSource(ms);
  }

  @Test
  void zhLocalization() {
    assertEquals("设备不存在: GJXA061", i18n.get(Locale.SIMPLIFIED_CHINESE, "error.iot.device_not_found", "GJXA061"));
    assertEquals("请求参数错误", i18n.get(Locale.SIMPLIFIED_CHINESE, "error.common.bad_request"));
  }

  @Test
  void enLocalization() {
    assertEquals("Device not found: GJXA061", i18n.get(Locale.US, "error.iot.device_not_found", "GJXA061"));
    assertEquals("Confirm", i18n.get(Locale.US, "common.action.confirm"));
  }

  @Test
  void unknownKeyReturnsKeyItself() {
    assertEquals("error.unknown.key", i18n.get(Locale.SIMPLIFIED_CHINESE, "error.unknown.key"));
  }

  /** Accept-Language 解析：无 header → 默认中文；en → 英文；zh → 中文（避免 Servlet 默认 JVM locale 干扰） */
  @Test
  void resolveLocale_fromAcceptLanguageHeader() throws Exception {
    org.springframework.mock.web.MockHttpServletRequest req =
        new org.springframework.mock.web.MockHttpServletRequest();
    org.springframework.web.context.request.ServletRequestAttributes attrs =
        new org.springframework.web.context.request.ServletRequestAttributes(req);
    org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(attrs);
    try {
      // 无 header → 默认中文
      assertEquals("请求参数错误", i18n.get("error.common.bad_request"));
      // en → 英文
      req.addHeader("Accept-Language", "en-US,en;q=0.9");
      assertEquals("Bad request", i18n.get("error.common.bad_request"));
      // zh → 中文
      req.removeHeader("Accept-Language");
      req.addHeader("Accept-Language", "zh-CN,zh;q=0.9");
      assertEquals("请求参数错误", i18n.get("error.common.bad_request"));
      // 未知语言 → 回退中文
      req.removeHeader("Accept-Language");
      req.addHeader("Accept-Language", "fr-FR");
      assertEquals("请求参数错误", i18n.get("error.common.bad_request"));
    } finally {
      org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }
  }
}
