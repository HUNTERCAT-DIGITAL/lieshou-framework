package cn.huntercat.lieshou.framework.service;

import cn.huntercat.lieshou.framework.domain.VerificationCode;

/**
 * 验证码发送器抽象（ADR-0023）.
 *
 * <p>生产实现：短信走阿里云 / 腾讯云短信，邮箱走 SMTP / 阿里云邮件（Phase 2）。 dev 环境用 {@link DevCodeSender} 打印日志，方便联调。
 */
public interface CodeSender {

  /** 发送验证码（target = 手机号或邮箱） */
  void send(VerificationCode.Channel channel, String target, String code);
}
