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
}
