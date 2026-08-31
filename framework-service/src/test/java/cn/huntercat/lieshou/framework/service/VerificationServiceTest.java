package cn.huntercat.lieshou.framework.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.domain.VerificationCode;
import cn.huntercat.lieshou.framework.domain.VerificationCodeRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** VerificationService 单测（验证码 · ADR-0023 核心）。 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

  @Mock private VerificationCodeRepository repo;
  @Mock private CodeSender sender;
  @InjectMocks private VerificationService service;

  private static VerificationCode code(String value, Instant createdAt, Instant expiresAt) {
    VerificationCode vc =
        new VerificationCode(
            VerificationCode.Channel.SMS,
            "13800000000",
            VerificationCode.Purpose.LOGIN,
            value,
            expiresAt);
    org.springframework.test.util.ReflectionTestUtils.setField(vc, "createdAt", createdAt);
    return vc;
  }

  @Test
  void send_生成6位码并保存发送() {
    when(repo.findTop2ByChannelAndTargetAndPurposeOrderByCreatedAtDesc(any(), anyString(), any()))
        .thenReturn(List.of());
    when(repo.save(any(VerificationCode.class))).thenAnswer(inv -> inv.getArgument(0));

    service.send(VerificationCode.Channel.SMS, "13800000000", VerificationCode.Purpose.LOGIN);

    verify(repo).save(any(VerificationCode.class));
    verify(sender).send(
        eq(VerificationCode.Channel.SMS),
        eq("13800000000"),
        anyString(),
        eq(VerificationCode.Purpose.LOGIN));
  }

  @Test
  void send_60秒内重发抛SEND_TOO_FREQUENT() {
    VerificationCode recent = code("123456", Instant.now(), Instant.now().plusSeconds(300));
    when(repo.findTop2ByChannelAndTargetAndPurposeOrderByCreatedAtDesc(any(), anyString(), any()))
        .thenReturn(List.of(recent));

    assertThatThrownBy(
            () ->
                service.send(
                    VerificationCode.Channel.SMS, "13800000000", VerificationCode.Purpose.LOGIN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SEND_TOO_FREQUENT");
    verify(repo, never()).save(any(VerificationCode.class));
  }

  @Test
  void verify_匹配码校验通过() {
    VerificationCode vc =
        code("123456", Instant.now().minusSeconds(10), Instant.now().plusSeconds(290));
    when(repo.findFirstByChannelAndTargetAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            any(), anyString(), any()))
        .thenReturn(Optional.of(vc));

    service.verify(
        VerificationCode.Channel.SMS, "13800000000", VerificationCode.Purpose.LOGIN, "123456");

    assertThat(vc.getUsedAt()).isNotNull(); // 一次性
    verify(repo).save(vc);
  }

  @Test
  void verify_错误码抛INVALID_CODE() {
    VerificationCode vc =
        code("123456", Instant.now().minusSeconds(10), Instant.now().plusSeconds(290));
    when(repo.findFirstByChannelAndTargetAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            any(), anyString(), any()))
        .thenReturn(Optional.of(vc));

    assertThatThrownBy(
            () ->
                service.verify(
                    VerificationCode.Channel.SMS,
                    "13800000000",
                    VerificationCode.Purpose.LOGIN,
                    "999999"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CODE_MISMATCH");
    assertThat(vc.getUsedAt()).isNull();
  }

  @Test
  void verify_过期抛EXPIRED() {
    VerificationCode vc =
        code("123456", Instant.now().minusSeconds(400), Instant.now().minusSeconds(100));
    when(repo.findFirstByChannelAndTargetAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            any(), anyString(), any()))
        .thenReturn(Optional.of(vc));

    assertThatThrownBy(
            () ->
                service.verify(
                    VerificationCode.Channel.SMS,
                    "13800000000",
                    VerificationCode.Purpose.LOGIN,
                    "123456"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CODE_EXPIRED");
  }

  @Test
  void verify_无记录抛CODE_NOT_FOUND() {
    when(repo.findFirstByChannelAndTargetAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(
            any(), anyString(), any()))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.verify(
                    VerificationCode.Channel.SMS,
                    "13800000000",
                    VerificationCode.Purpose.LOGIN,
                    "123456"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CODE_NOT_FOUND");
  }
}
