package cn.huntercat.lieshou.framework.service;

import cn.huntercat.lieshou.framework.domain.VerificationCode;

/**
 * 验证码发送器抽象（ADR-0023）.
 *
 * <p>生产实现：短信走阿里云 / 腾讯云短信，邮箱走 SMTP / 阿里云邮件（Phase 2）。 框架内置 {@link DevCodeSender} 作为未注册时的默认实现（仅打印日志，方便
 * dev 联调）；消费方注册自己的 bean 即覆盖。
 */
public interface CodeSender {

  /** 发送验证码（target = 手机号或邮箱） */
  void send(VerificationCode.Channel channel, String target, String code);

  /**
   * 发送验证码（带用途，便于消费方按 purpose 选模板/渠道，如登录验证码 vs 改密验证码）。
   *
   * <p>默认转发到旧方法（向后兼容）；需要区分用途的消费方重写本方法。
   */
  default void send(
      VerificationCode.Channel channel,
      String target,
      String code,
      VerificationCode.Purpose purpose) {
    send(channel, target, code);
  }
}
