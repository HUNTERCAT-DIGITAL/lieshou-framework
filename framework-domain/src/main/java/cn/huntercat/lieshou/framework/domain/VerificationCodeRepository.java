package cn.huntercat.lieshou.framework.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 验证码 Repository（ADR-0023）. */
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

  /** 查某 target 最新未使用且未过期的验证码（用途 + 渠道维度） */
  Optional<VerificationCode>
      findFirstByChannelAndTargetAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
          VerificationCode.Channel channel, String target, VerificationCode.Purpose purpose);

  /** 查某 target 最近创建的验证码（频率限制用） */
  List<VerificationCode> findTop2ByChannelAndTargetAndPurposeOrderByCreatedAtDesc(
      VerificationCode.Channel channel, String target, VerificationCode.Purpose purpose);

  /** 清理过期记录 */
  void deleteByExpiresAtBefore(Instant cutoff);
}
