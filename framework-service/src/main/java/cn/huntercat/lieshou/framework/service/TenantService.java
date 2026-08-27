package cn.huntercat.lieshou.framework.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import cn.huntercat.lieshou.framework.domain.Tenant;
import cn.huntercat.lieshou.framework.domain.TenantRepository;
import cn.huntercat.lieshou.framework.domain.UserRepository;
import java.util.List;

/**
 * 租户生命周期业务（多租户 · ADR-0022 · 三套产品线共用）.
 *
 * <p>从两端 Controller 内联收敛：开租户（code 唯一 + edition 校验）、更新（name/status/edition 枚举校验）、删除（仅空租户可删，有用户建议
 * DISABLED）、自助开通复用 {@link TenantRegistrationService}。错误抛 {@link BaseException}（错误码契约：
 * TENANT_CODE_TAKEN / INVALID_EDITION / INVALID_STATUS / REGISTER_INVALID / NOT_FOUND / CONFLICT），由
 * GlobalExceptionHandler 统一转 {error, message} + 状态码。 PLATFORM_ADMIN 权限校验与审计记录属 HTTP 层，保留在薄壳
 * Controller。
 */
@Service
public class TenantService {

  private final TenantRepository repo;
  private final UserRepository userRepo;
  private final TenantRegistrationService registration;

  public TenantService(
      TenantRepository repo, UserRepository userRepo, TenantRegistrationService registration) {
    this.repo = repo;
    this.userRepo = userRepo;
    this.registration = registration;
  }

  /** 全部租户（无分页）。 */
  @Transactional(readOnly = true)
  public List<Tenant> list() {
    return repo.findAll();
  }

  /** 按 id 查租户；不存在抛 NOT_FOUND。 */
  @Transactional(readOnly = true)
  public Tenant get(Long id) {
    return repo.findById(id).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "租户不存在"));
  }

  /** 自助开通（公开）：租户 + 管理员；非法输入抛 REGISTER_INVALID（message 透传）。 */
  @Transactional
  public TenantRegistrationService.RegistrationResult register(
      String tenantName,
      String tenantCode,
      String username,
      String displayName,
      String password,
      String email) {
    try {
      return registration.register(tenantName, tenantCode, username, displayName, password, email);
    } catch (IllegalArgumentException e) {
      throw new BaseException(
          "REGISTER_INVALID",
          HttpStatus.BAD_REQUEST,
          e.getMessage() == null ? "注册参数不合法" : e.getMessage());
    }
  }

  /** 开租户（code 唯一；edition 可选默认 GENERIC）。 */
  @Transactional
  public Tenant create(String name, String code, String edition) {
    if (repo.findByCode(code).isPresent()) {
      throw new BaseException("TENANT_CODE_TAKEN", HttpStatus.BAD_REQUEST, "租户编码已存在: " + code);
    }
    Tenant.Edition resolvedEdition = parseEdition(edition);
    if (edition != null && !edition.isBlank() && resolvedEdition == null) {
      throw new BaseException("INVALID_EDITION", HttpStatus.BAD_REQUEST, "租户版别不合法");
    }
    return repo.save(new Tenant(name, code, resolvedEdition));
  }

  /** 更新租户（name/status/edition 均可选；枚举非法抛 INVALID_STATUS / INVALID_EDITION）。 */
  @Transactional
  public Tenant update(Long id, String name, String status, String edition) {
    Tenant tenant =
        repo.findById(id).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "租户不存在"));
    if (name != null && !name.isBlank()) {
      tenant.setName(name);
    }
    if (status != null && !status.isBlank()) {
      tenant.setStatus(parseEnum(status, Tenant.Status.class, "INVALID_STATUS", "租户状态不合法"));
    }
    if (edition != null && !edition.isBlank()) {
      tenant.setEdition(parseEnum(edition, Tenant.Edition.class, "INVALID_EDITION", "租户版别不合法"));
    }
    return repo.save(tenant);
  }

  /** 删除租户（仅无用户时允许；有用户 → CONFLICT，建议改停用 DISABLED）。返回被删租户供审计记录。 */
  @Transactional
  public Tenant delete(Long id) {
    Tenant tenant =
        repo.findById(id).orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "租户不存在"));
    long userCount = userRepo.countByTenantId(id);
    if (userCount > 0) {
      throw new BaseException(ErrorCode.CONFLICT, "租户仍有关联用户，请改用停用（status=DISABLED）");
    }
    repo.delete(tenant);
    return tenant;
  }

  /** 解析版别（空 → GENERIC；非法 → null 由调用方判定）。 */
  private static Tenant.Edition parseEdition(String edition) {
    if (edition == null || edition.isBlank()) {
      return Tenant.Edition.GENERIC;
    }
    try {
      return Tenant.Edition.valueOf(edition);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static <E extends Enum<E>> E parseEnum(
      String value, Class<E> type, String errorCode, String message) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException e) {
      throw new BaseException(errorCode, HttpStatus.BAD_REQUEST, message);
    }
  }
}
