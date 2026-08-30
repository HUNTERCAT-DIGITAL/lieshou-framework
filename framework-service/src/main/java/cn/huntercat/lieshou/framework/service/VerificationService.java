package cn.huntercat.lieshou.framework.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.domain.VerificationCode;
import cn.huntercat.lieshou.framework.domain.VerificationCodeRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 验证码服务（ADR-0023）：生成 / 发送 / 校验，一次性 + 过期 + 频率限制. */
@Service
public class VerificationService {

  private static final Duration TTL = Duration.ofMinutes(5);
  private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final VerificationCodeRepository repo;
  private final CodeSender sender;

  public VerificationService(VerificationCodeRepository repo, CodeSender sender) {
    this.repo = repo;
    this.sender = sender;
  }

  /**
   * 发送验证码（频率限制：同一 target+purpose 60s 内不可重复发）。
   *
   * @throws IllegalStateException 发送过于频繁
   */
  @Transactional
  public void send(
      VerificationCode.Channel channel, String target, VerificationCode.Purpose purpose) {
    List<VerificationCode> recent =
        repo.findTop2ByChannelAndTargetAndPurposeOrderByCreatedAtDesc(channel, target, purpose);
    if (!recent.isEmpty()) {
      VerificationCode latest = recent.get(0);
      if (latest.getCreatedAt().plus(RESEND_INTERVAL).isAfter(Instant.now())) {
        throw new IllegalStateException("SEND_TOO_FREQUENT");
      }
    }

    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    VerificationCode vc =
        new VerificationCode(channel, target, purpose, code, Instant.now().plus(TTL));
    repo.save(vc);
    sender.send(channel, target, code, purpose);
  }

  /**
   * 校验验证码：最新未用未过期且匹配 → 标记已用。
   *
   * @throws IllegalArgumentException 验证码无效 / 过期 / 已使用
   */
  @Transactional
  public void verify(
      VerificationCode.Channel channel,
      String target,
      VerificationCode.Purpose purpose,
      String code) {
    VerificationCode vc =
        repo.findFirstByChannelAndTargetAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
                channel, target, purpose)
            .orElseThrow(() -> new IllegalArgumentException("CODE_NOT_FOUND"));
    if (vc.isExpired()) {
      throw new IllegalArgumentException("CODE_EXPIRED");
    }
    if (!vc.getCode().equals(code)) {
      throw new IllegalArgumentException("CODE_MISMATCH");
    }
    vc.setUsedAt(Instant.now());
    repo.save(vc);
  }
}
