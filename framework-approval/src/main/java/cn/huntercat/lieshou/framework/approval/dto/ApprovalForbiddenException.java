package cn.huntercat.lieshou.framework.approval.dto;

import org.springframework.http.HttpStatus;

import cn.huntercat.lieshou.framework.common.api.BaseException;

/** 审批业务异常（由 framework-common GlobalExceptionHandler 转 HTTP 响应） */
public class ApprovalForbiddenException extends BaseException {
  public ApprovalForbiddenException(String message) {
    super("FORBIDDEN", HttpStatus.FORBIDDEN, message);
  }
}
