package cn.huntercat.lieshou.framework.approval.dto;

import org.springframework.http.HttpStatus;

import cn.huntercat.lieshou.framework.common.api.BaseException;

/** 审批业务异常（由 framework-common GlobalExceptionHandler 转 HTTP 响应） */
public class ApproverResolveException extends BaseException {
  public ApproverResolveException(String message) {
    super("APPROVER_RESOLVE_FAILED", HttpStatus.BAD_REQUEST, message);
  }
}
