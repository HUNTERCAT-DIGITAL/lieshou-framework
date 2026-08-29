package cn.huntercat.lieshou.framework.i18n;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 多语言自动装配（L0 · S5）.
 *
 * <p>加载 {@code i18n/messages} 语言包（{@code lieshou-i18n} 生成器产出的 {@code messages_zh_CN.properties} /
 * {@code messages_en_US.properties}）：zh-CN 为兜底语言，UTF-8 编码，未匹配语言回退中文。
 */
@AutoConfiguration
public class I18nAutoConfiguration {

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
    ms.setBasename("i18n/messages");
    ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
    ms.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
    ms.setFallbackToSystemLocale(false);
    return ms;
  }
}
