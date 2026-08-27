package cn.huntercat.lieshou.framework.approval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.huntercat.lieshou.framework.approval.domain.ApprovalAuditLogRepository;
import cn.huntercat.lieshou.framework.approval.domain.ApprovalRequest;
import cn.huntercat.lieshou.framework.approval.domain.ApprovalRequestRepository;
import cn.huntercat.lieshou.framework.approval.dto.AlreadyDecidedException;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalDtos.CreateApprovalRequest;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalDtos.DecideRequest;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalDtos.RejectRequest;
import cn.huntercat.lieshou.framework.approval.dto.ApprovalForbiddenException;
import cn.huntercat.lieshou.framework.approval.dto.ApproverResolveException;
import cn.huntercat.lieshou.framework.approval.port.NotifierPort;
import cn.huntercat.lieshou.framework.approval.port.UserQueryPort;
import cn.huntercat.lieshou.framework.approval.port.UserView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 审批状态机核心测试（PENDING → APPROVED/REJECTED/CANCELLED · 权限/租户隔离） */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

  @Mock private ApprovalRequestRepository repo;
  @Mock private ApprovalAuditLogRepository auditRepo;
  @Mock private ApprovalAuditService auditService;
  @Mock private UserQueryPort userClient;
  @Mock private NotifierPort notifier;

  private ApprovalService service;

  @BeforeEach
  void setUp() {
    service = new ApprovalService(repo, auditRepo, auditService, userClient, notifier);
    lenient().when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private ApprovalRequest pendingRequest(
      Long id, Long tenantId, Long requesterId, Long approverId) {
    ApprovalRequest a =
        new ApprovalRequest(
            tenantId,
            ApprovalRequest.Type.EXPENSE,
            "报销",
            new BigDecimal("100.00"),
            requesterId,
            approverId);
    a.setId(id);
    return a;
  }

  private CreateApprovalRequest createReq(Long approverId) {
    return new CreateApprovalRequest("EXPENSE", "差旅报销", new BigDecimal("120.00"), "出差", approverId);
  }

  @Test
  void create_withExplicitApprover_savesAndNotifies() {
    when(repo.save(any(ApprovalRequest.class))).thenAnswer(inv -> inv.getArgument(0));
    ApprovalRequest saved = service.create(1L, 1L, createReq(2L), "127.0.0.1", "test", null);

    assertThat(saved.getTenantId()).isEqualTo(1L);
    assertThat(saved.getRequesterId()).isEqualTo(1L);
    assertThat(saved.getApproverId()).isEqualTo(2L);
    assertThat(saved.getStatus()).isEqualTo(ApprovalRequest.Status.PENDING);
    verify(notifier).notifyApprover(1L, saved);
  }

  @Test
  void create_noUsers_throwsApproverResolve() {
    when(userClient.listTenantUsers("1")).thenReturn(List.of());
    assertThatThrownBy(() -> service.create(1L, 1L, createReq(null), "ip", "ua", null))
        .isInstanceOf(ApproverResolveException.class);
  }

  @Test
  void create_invalidType_throwsInvalidType() {
    CreateApprovalRequest bad =
        new CreateApprovalRequest("FIREWORKS", "x", new BigDecimal("1"), null, 2L);
    assertThatThrownBy(() -> service.create(1L, 1L, bad, "ip", "ua", null))
        .isInstanceOf(cn.huntercat.lieshou.framework.approval.dto.InvalidTypeException.class);
  }

  @Test
  void approve_byApprover_transitionsToApproved() {
    ApprovalRequest a = pendingRequest(10L, 1L, 1L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(a));
    when(repo.save(a)).thenReturn(a);

    ApprovalRequest saved = service.approve(10L, 1L, 2L, new DecideRequest("ok"), "ip", "ua", null);

    assertThat(saved.getStatus()).isEqualTo(ApprovalRequest.Status.APPROVED);
    assertThat(saved.getDecidedBy()).isEqualTo(2L);
    verify(notifier).notifyRequester(1L, saved, "通过");
  }

  @Test
  void approve_byNonApprover_throwsForbidden() {
    ApprovalRequest a = pendingRequest(10L, 1L, 1L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(a));
    assertThatThrownBy(
            () -> service.approve(10L, 1L, 3L, new DecideRequest(null), "ip", "ua", null))
        .isInstanceOf(ApprovalForbiddenException.class);
  }

  @Test
  void approve_alreadyDecided_throws() {
    ApprovalRequest a = pendingRequest(10L, 1L, 1L, 2L);
    a.setStatus(ApprovalRequest.Status.APPROVED);
    when(repo.findById(10L)).thenReturn(Optional.of(a));
    assertThatThrownBy(
            () -> service.approve(10L, 1L, 2L, new DecideRequest(null), "ip", "ua", null))
        .isInstanceOf(AlreadyDecidedException.class);
  }

  @Test
  void reject_byApprover_transitionsToRejected() {
    ApprovalRequest a = pendingRequest(10L, 1L, 1L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(a));
    when(repo.save(a)).thenReturn(a);
    ApprovalRequest saved =
        service.reject(10L, 1L, 2L, new RejectRequest("资料不全"), "ip", "ua", null);
    assertThat(saved.getStatus()).isEqualTo(ApprovalRequest.Status.REJECTED);
    assertThat(saved.getComment()).isEqualTo("资料不全");
  }

  @Test
  void cancel_byRequester_transitionsToCancelled() {
    ApprovalRequest a = pendingRequest(10L, 1L, 1L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(a));
    when(repo.save(a)).thenReturn(a);
    ApprovalRequest saved = service.cancel(10L, 1L, 1L, new DecideRequest(null), "ip", "ua", null);
    assertThat(saved.getStatus()).isEqualTo(ApprovalRequest.Status.CANCELLED);
  }

  @Test
  void cancel_byNonRequester_throwsForbidden() {
    ApprovalRequest a = pendingRequest(10L, 1L, 1L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(a));
    assertThatThrownBy(() -> service.cancel(10L, 1L, 9L, new DecideRequest(null), "ip", "ua", null))
        .isInstanceOf(ApprovalForbiddenException.class);
  }

  @Test
  void get_crossTenant_throwsNotFound() {
    ApprovalRequest a = pendingRequest(10L, 1L, 1L, 2L);
    when(repo.findById(10L)).thenReturn(Optional.of(a));
    assertThatThrownBy(() -> service.get(10L, 99L))
        .isInstanceOf(cn.huntercat.lieshou.framework.approval.dto.NotFoundException.class);
  }

  @Test
  void list_autoResolveApprover_fallsBackToFirstAdmin() {
    when(userClient.listTenantUsers("1"))
        .thenReturn(
            List.of(
                new UserView(5L, "u5", "User5", "u5@x", "ACTIVE", List.of("USER")),
                new UserView(4L, "u4", "User4", "u4@x", "ACTIVE", List.of("TENANT_ADMIN"))));
    ApprovalRequest saved = service.create(1L, 1L, createReq(null), "ip", "ua", null);
    assertThat(saved.getApproverId()).isEqualTo(4L);
  }
}
