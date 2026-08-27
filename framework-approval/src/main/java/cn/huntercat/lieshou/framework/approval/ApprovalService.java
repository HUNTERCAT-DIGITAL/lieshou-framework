package cn.huntercat.lieshou.framework.approval;

import org.springframework.stereotype.Service;

import cn.huntercat.lieshou.framework.approval.domain.ApprovalAuditLog;
import cn.huntercat.lieshou.framework.approval.domain.ApprovalAuditLogRepository;
import cn.huntercat.lieshou.framework.approval.domain.ApprovalRequest;
import cn.huntercat.lieshou.framework.approval.domain.ApprovalRequestRepository;
import cn.huntercat.lieshou.framework.approval.dto.AlreadyDecidedException;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalDtos.CreateApprovalRequest;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalDtos.DecideRequest;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalDtos.RejectRequest;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalForbiddenException;
import cn.huntercat.lieshou.framework.approval.dto.ApproverResolveException;
import cn.huntercat.lieshou.framework.approval.dto.InvalidTypeException;
import cn.huntercat.lieshou.framework.approval.dto.NotFoundException;
import cn.huntercat.lieshou.framework.approval.port.NotifierPort;
import cn.huntercat.lieshou.framework.approval.port.UserQueryPort;
import cn.huntercat.lieshou.framework.approval.port.UserView;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 审批业务核心（上游同源唯一）。
 *
 * <p>状态机（ADR-0032）：PENDING → APPROVED / REJECTED / CANCELLED；租户内强制隔离； 审计（ApprovalAuditService）+
 * 通知（NotifierPort）贯穿。 web 层（租户/用户头解析、clientIp）由消费方 Controller 负责。
 */
@Service
public class ApprovalService {

  private final ApprovalRequestRepository repo;
  private final ApprovalAuditLogRepository auditRepo;
  private final ApprovalAuditService auditService;
  private final UserQueryPort userClient;
  private final NotifierPort notifier;

  public ApprovalService(
      ApprovalRequestRepository repo,
      ApprovalAuditLogRepository auditRepo,
      ApprovalAuditService auditService,
      UserQueryPort userClient,
      NotifierPort notifier) {
    this.repo = repo;
    this.auditRepo = auditRepo;
    this.auditService = auditService;
    this.userClient = userClient;
    this.notifier = notifier;
  }

  /** 发起审批（approverId 可空：租户管理员兜底） */
  public ApprovalRequest create(
      Long tenantId,
      Long requesterId,
      CreateApprovalRequest body,
      String clientIp,
      String userAgent,
      String requestId) {
    Long approverId = resolveApprover(tenantId, body.approverId());
    if (approverId == null) {
      throw new ApproverResolveException("无法解析审批人（租户无可用用户）");
    }
    ApprovalRequest a =
        new ApprovalRequest(
            tenantId,
            parseTypeRequired(body.type()),
            body.title().trim(),
            body.amount(),
            requesterId,
            approverId);
    if (body.detail() != null && !body.detail().isBlank()) a.setDetail(body.detail());
    ApprovalRequest saved = repo.save(a);
    auditService.recordSuccess(
        tenantId,
        requesterId,
        ApprovalAuditLog.Action.CREATE,
        saved.getId(),
        "发起审批 " + saved.getTitle(),
        clientIp,
        userAgent,
        requestId);
    notifier.notifyApprover(tenantId, saved);
    return saved;
  }

  /** 通过（PENDING → APPROVED · 仅审批人） */
  public ApprovalRequest approve(
      Long id,
      Long tenantId,
      Long userId,
      DecideRequest body,
      String clientIp,
      String userAgent,
      String requestId) {
    ApprovalRequest a = findTenantRequest(id, tenantId);
    requirePending(a, "approve");
    requireApprover(a, userId);
    a.setStatus(ApprovalRequest.Status.APPROVED);
    if (body != null && body.comment() != null && !body.comment().isBlank())
      a.setComment(body.comment());
    decide(a, userId);
    ApprovalRequest saved = repo.save(a);
    auditService.recordSuccess(
        tenantId,
        userId,
        ApprovalAuditLog.Action.APPROVE,
        saved.getId(),
        "通过审批 " + saved.getTitle(),
        clientIp,
        userAgent,
        requestId);
    notifier.notifyRequester(tenantId, saved, "通过");
    return saved;
  }

  /** 驳回（PENDING → REJECTED · 仅审批人 · comment 必填） */
  public ApprovalRequest reject(
      Long id,
      Long tenantId,
      Long userId,
      RejectRequest body,
      String clientIp,
      String userAgent,
      String requestId) {
    ApprovalRequest a = findTenantRequest(id, tenantId);
    requirePending(a, "reject");
    requireApprover(a, userId);
    a.setStatus(ApprovalRequest.Status.REJECTED);
    a.setComment(body.comment());
    decide(a, userId);
    ApprovalRequest saved = repo.save(a);
    auditService.recordSuccess(
        tenantId,
        userId,
        ApprovalAuditLog.Action.REJECT,
        saved.getId(),
        "驳回审批 " + saved.getTitle(),
        clientIp,
        userAgent,
        requestId);
    notifier.notifyRequester(tenantId, saved, "驳回");
    return saved;
  }

  /** 撤销（PENDING → CANCELLED · 仅发起人） */
  public ApprovalRequest cancel(
      Long id,
      Long tenantId,
      Long userId,
      DecideRequest body,
      String clientIp,
      String userAgent,
      String requestId) {
    ApprovalRequest a = findTenantRequest(id, tenantId);
    requirePending(a, "cancel");
    if (!a.getRequesterId().equals(userId)) {
      throw new ApprovalForbiddenException("只有发起人才能撤销审批");
    }
    a.setStatus(ApprovalRequest.Status.CANCELLED);
    decide(a, userId);
    ApprovalRequest saved = repo.save(a);
    auditService.recordSuccess(
        tenantId,
        userId,
        ApprovalAuditLog.Action.CANCEL,
        saved.getId(),
        "撤销审批 " + saved.getTitle(),
        clientIp,
        userAgent,
        requestId);
    return saved;
  }

  /** 查询单个（租户内隔离 · 404 防枚举） */
  public ApprovalRequest get(Long id, Long tenantId) {
    return findTenantRequest(id, tenantId);
  }

  /** 列表（租户内 · role: mine=我发起 / inbox=待我审批 / 其他=全部；type/status 过滤） */
  public List<ApprovalRequest> list(
      Long tenantId, Long userId, String role, String type, String status) {
    ApprovalRequest.Type typeFilter = parseType(type);
    ApprovalRequest.Status statusFilter = parseStatus(status);
    if ("mine".equals(role)) {
      return userId == null ? List.of() : repo.findByRequester(tenantId, userId, statusFilter);
    }
    if ("inbox".equals(role)) {
      return userId == null ? List.of() : repo.findInbox(tenantId, userId);
    }
    return repo.findTenantRequests(tenantId, typeFilter, statusFilter);
  }

  /** 待办计数（工作台角标：inbox=待我审批 / mine=我发起待审批） */
  public Map<String, Long> counts(Long tenantId, Long userId) {
    long inbox = 0L;
    long mine = 0L;
    if (userId != null) {
      inbox =
          repo.countByTenantIdAndApproverIdAndStatus(
              tenantId, userId, ApprovalRequest.Status.PENDING);
      mine =
          repo.countByTenantIdAndRequesterIdAndStatus(
              tenantId, userId, ApprovalRequest.Status.PENDING);
    }
    return Map.of("inbox", inbox, "mine", mine);
  }

  /** 审批审计日志（append-only · 租户内 · 新→旧） */
  public List<ApprovalAuditLog> auditLogs(Long tenantId, int limit) {
    return auditRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
  }

  // ============ 内部辅助 ============

  private ApprovalRequest findTenantRequest(Long id, Long tenantId) {
    Optional<ApprovalRequest> opt = repo.findById(id);
    if (opt.isEmpty() || !opt.get().getTenantId().equals(tenantId)) {
      throw new NotFoundException("审批单不存在");
    }
    return opt.get();
  }

  private void requirePending(ApprovalRequest a, String action) {
    if (a.getStatus() != ApprovalRequest.Status.PENDING) {
      throw new AlreadyDecidedException("审批单已" + statusText(a.getStatus()) + "，无法" + action);
    }
  }

  private void requireApprover(ApprovalRequest a, Long userId) {
    if (!a.getApproverId().equals(userId)) {
      throw new ApprovalForbiddenException("只有被指定的审批人才能审批该单据");
    }
  }

  private void decide(ApprovalRequest a, Long userId) {
    a.setDecidedBy(userId);
    a.setDecidedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
  }

  private String statusText(ApprovalRequest.Status s) {
    return switch (s) {
      case APPROVED -> "通过";
      case REJECTED -> "驳回";
      case CANCELLED -> "撤销";
      default -> "处理";
    };
  }

  private ApprovalRequest.Status parseStatus(String value) {
    try {
      return value == null ? null : ApprovalRequest.Status.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private ApprovalRequest.Type parseType(String value) {
    try {
      return ApprovalRequest.Type.valueOf(value == null ? "" : value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private ApprovalRequest.Type parseTypeRequired(String value) {
    ApprovalRequest.Type t = parseType(value);
    if (t == null) throw new InvalidTypeException("非法审批类型");
    return t;
  }

  private Long resolveApprover(Long tenantId, Long requested) {
    if (requested != null) return requested;
    try {
      List<UserView> users = userClient.listTenantUsers(String.valueOf(tenantId));
      if (users == null || users.isEmpty()) return null;
      return users.stream()
          .filter(u -> u.roles() != null && u.roles().contains("TENANT_ADMIN"))
          .findFirst()
          .map(UserView::id)
          .orElseGet(() -> users.stream().findFirst().map(UserView::id).orElse(null));
    } catch (Exception e) {
      return null;
    }
  }
}
