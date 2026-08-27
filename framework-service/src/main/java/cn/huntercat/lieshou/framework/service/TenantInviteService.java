package cn.huntercat.lieshou.framework.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import cn.huntercat.lieshou.framework.domain.TenantInvite;
import cn.huntercat.lieshou.framework.domain.TenantInviteRepository;
import cn.huntercat.lieshou.framework.domain.TenantRepository;

/**
 * 租户邀请码业务（ADR-0023 Phase 2 · 三套产品线共用）.
 *
 * <p>从两端 Controller 内联收敛：唯一邀请码生成（去易混淆字符集 + 冲突重试）、租户存在性校验、
 * role 白名单、过期时间计算、revoke（租户维度防越权）。错误抛 {@link BaseException}
 * （错误码契约：INVALID_ROLE / NOT_FOUND / FAILED_TO_GENERATE_INVITE）。
 */
@Service
public class TenantInviteService {

  /** 邀请码字符集（去易混淆 I/O/0/1） */
  private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  private static final int CODE_LENGTH = 8;
  private static final int MAX_GENERATE_ATTEMPTS = 20;
  private static final List<String> ALLOWED_ROLES = List.of("USER", "ADMIN");
  private static final SecureRandom RANDOM = new SecureRandom();

  private final TenantRepository tenantRepo;
  private final TenantInviteRepository inviteRepo;

  public TenantInviteService(TenantRepository tenantRepo, TenantInviteRepository inviteRepo) {
    this.tenantRepo = tenantRepo;
    this.inviteRepo = inviteRepo;
  }

  /** 生成邀请码（租户须存在；role 白名单 USER/ADMIN）。 */
  @Transactional
  public TenantInvite create(Long tenantId, String role, Integer expiresInDays, Integer maxUses) {
    if (tenantRepo.findById(tenantId).isEmpty()) {
      throw new BaseException(ErrorCode.NOT_FOUND, "租户不存在");
    }
    String resolvedRole = (role == null || role.isBlank()) ? "USER" : role;
    if (!ALLOWED_ROLES.contains(resolvedRole)) {
      throw new BaseException("INVALID_ROLE", HttpStatus.BAD_REQUEST, "邀请码角色仅支持 USER / ADMIN");
    }
    Instant expiresAt =
        (expiresInDays == null || expiresInDays <= 0)
            ? null
            : Instant.now().plusSeconds(expiresInDays * 86400L);
    String code = generateUniqueCode();
    TenantInvite invite = new TenantInvite(tenantId, code, resolvedRole, expiresAt, maxUses, null);
    return inviteRepo.save(invite);
  }

  /** 租户邀请码列表（新→旧；租户须存在）。 */
  @Transactional(readOnly = true)
  public List<TenantInvite> list(Long tenantId) {
    if (tenantRepo.findById(tenantId).isEmpty()) {
      throw new BaseException(ErrorCode.NOT_FOUND, "租户不存在");
    }
    return inviteRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  /** 撤销邀请码（租户维度限定，防跨租户越权）。 */
  @Transactional
  public void revoke(Long tenantId, Long id) {
    TenantInvite invite =
        inviteRepo
            .findById(id)
            .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "邀请码不存在"));
    if (!invite.getTenantId().equals(tenantId)) {
      throw new BaseException(ErrorCode.NOT_FOUND, "邀请码不存在");
    }
    invite.setRevokedAt(Instant.now());
    inviteRepo.save(invite);
  }

  /** 生成不与现有冲突的邀请码（20 次重试后放弃）。 */
  private String generateUniqueCode() {
    for (int attempt = 0; attempt < MAX_GENERATE_ATTEMPTS; attempt++) {
      StringBuilder sb = new StringBuilder(CODE_LENGTH);
      for (int i = 0; i < CODE_LENGTH; i++) {
        sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
      }
      String code = sb.toString();
      if (inviteRepo.findByCode(code).isEmpty()) {
        return code;
      }
    }
    throw new BaseException(
        "FAILED_TO_GENERATE_INVITE", HttpStatus.INTERNAL_SERVER_ERROR, "邀请码生成失败");
  }
}
