package cn.huntercat.lieshou.framework.common.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * SMTP 邮件客户端（统一配置 · Bottom-Up 抽象）.
 *
 * <p>自 user-service（验证码通道）与 approval-service（审批通知）两处同源实现下沉； 采用 approval 的<b>条件创建</b>模式（更优）：仅配置了
 * {@code EMAIL_SMTP_HOST} 才创建 bean —— 通知/验证码是附属能力，缺失时主流程照常工作（发送方需 {@code @ConditionalOnBean}
 * 空安全降级）。
 *
 * <p>同套环境变量（与 user/approval 历史一致）：{@code EMAIL_SMTP_HOST} / {@code EMAIL_SMTP_USER} / {@code
 * EMAIL_SMTP_PASS} / {@code EMAIL_SMTP_PORT}(默认 465 SSL)。
 *
 * <p>仅 prod profile 生效。本配置所在模块（common）的 mail starter 依赖为 optional， 消费服务需自行声明 {@code
 * spring-boot-starter-mail}（类路径守卫 {@code @ConditionalOnClass}）。
 */
@Configuration
@Profile("prod")
@ConditionalOnProperty(name = "EMAIL_SMTP_HOST")
@ConditionalOnClass(JavaMailSender.class)
public class SmtpMailConfig {

  @Bean
  public JavaMailSender javaMailSender(
      @Value("${EMAIL_SMTP_HOST}") String host,
      @Value("${EMAIL_SMTP_PORT:465}") int port,
      @Value("${EMAIL_SMTP_USER}") String username,
      @Value("${EMAIL_SMTP_PASS}") String password) {
    JavaMailSenderImpl sender = new JavaMailSenderImpl();
    sender.setHost(host);
    sender.setPort(port);
    sender.setUsername(username);
    sender.setPassword(password);
    Properties props = sender.getJavaMailProperties();
    props.put("mail.transport.protocol", "smtp");
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.ssl.enable", "true");
    props.put("mail.smtp.connectiontimeout", 10000);
    props.put("mail.smtp.timeout", 10000);
    props.put("mail.smtp.writetimeout", 10000);
    return sender;
  }
}
