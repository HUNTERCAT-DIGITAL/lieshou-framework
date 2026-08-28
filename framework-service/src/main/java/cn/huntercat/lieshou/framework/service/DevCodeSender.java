package cn.huntercat.lieshou.framework.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.huntercat.lieshou.framework.domain.VerificationCode;

/**
 * 默认验证码发送器：仅打印日志（dev 联调用）。
 *
 * <p>消费方未注册自己的 {@link CodeSender} 时兜底，保证 {@link VerificationService} 可启动； 生产环境（短信/邮件）由消费方注册 {@code
 * CodeSender} bean 覆盖本实现（与 {@code LoggingAuditRecorder @ConditionalOnMissingBean} 同模式）。
 */
@Component
@ConditionalOnMissingBean(CodeSender.class)
public class DevCodeSender implements CodeSender {

  private static final Logger log = LoggerFactory.getLogger("DEV-CODE");

  @Override
  public void send(VerificationCode.Channel channel, String target, String code) {
    log.info(
        "验证码发送（dev 默认实现）：channel={} target={} code={} —— 生产环境请注册 CodeSender bean 覆盖",
        channel,
        target,
        code);
  }
}
