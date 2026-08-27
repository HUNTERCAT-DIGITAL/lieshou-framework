package cn.huntercat.lieshou.framework.approval.dto;

import org.springframework.http.HttpStatus;

import cn.huntercat.lieshou.framework.common.api.BaseException;

/** 审批业务异常（由 framework-common GlobalExceptionHandler 转 HTTP 响应） */
public class NotFoundException extends BaseException {
  public NotFoundException(String message) {
    super("APPROVAL_NOT_FOUND", HttpStatus.NOT_FOUND, message);
  }
}
