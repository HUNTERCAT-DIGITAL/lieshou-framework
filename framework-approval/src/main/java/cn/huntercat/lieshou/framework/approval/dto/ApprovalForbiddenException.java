package cn.huntercat.lieshou.framework.approval.dto;

/** 审批业务异常 */
public class ApprovalForbiddenException extends RuntimeException {
  public ApprovalForbiddenException(String message) { super(message); }
}
