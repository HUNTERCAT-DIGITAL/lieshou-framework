package cn.huntercat.lieshou.framework.approval.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 审批请求 DTO（与微服务/单体共用） */
public final class ApprovalDtos {

  private ApprovalDtos() {}

  /** 发起审批请求 */
  public record CreateApprovalRequest(
      @NotBlank @Size(max = 16) String type,
      @NotBlank @Size(max = 128) String title,
      @DecimalMin("0.01") BigDecimal amount,
      @Size(max = 2000) String detail,
      @Min(1) Long approverId) {}

  /** 审批/撤销请求（approve/cancel 时 comment 可选） */
  public record DecideRequest(@Size(max = 500) String comment) {}

  /** 驳回请求（comment 必填） */
  public record RejectRequest(@NotBlank @Size(max = 500) String comment) {}
}
