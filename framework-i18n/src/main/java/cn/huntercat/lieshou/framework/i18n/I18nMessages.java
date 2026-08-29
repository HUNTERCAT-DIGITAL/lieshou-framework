package cn.huntercat.lieshou.framework.i18n;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 文案门面（L0 · S5）.
 *
 * <p>业务代码不写文案，只写 key：{@code i18nMessages.get("iot.device.not_found", deviceKey)}；
 * 按当前请求 Locale（Accept-Language）本地化。key 不存在时原样返回 key（不抛错，便于排查）。
 *
 * <p>数据源：{@code lieshou-i18n} 生成器产出的 {@code i18n/messages_*.properties}（本模块 classpath 内），
 * 前端 {@code @lieshoucloud/i18n} 与后端共享同一套 key。
 */
@Component
public class I18nMessages implements MessageSourceAware {

  private MessageSource messageSource;

  @Override
  public void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  /** 按当前请求 Locale 取文案（无 key → 返回 key 本身） */
  public String get(String key, Object... args) {
    return messageSource.getMessage(key, args, key, resolveLocale());
  }

  /** 指定 Locale 取文案（异步/批处理场景用） */
  public String get(Locale locale, String key, Object... args) {
    return messageSource.getMessage(key, args, key, locale != null ? locale : Locale.SIMPLIFIED_CHINESE);
  }

  /** 请求上下文存在时取 Accept-Language，否则默认中文（产品主语言） */
  private Locale resolveLocale() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      Locale locale = attrs.getRequest().getLocale();
      if (locale != null) return locale;
    }
    return Locale.SIMPLIFIED_CHINESE;
  }
}
