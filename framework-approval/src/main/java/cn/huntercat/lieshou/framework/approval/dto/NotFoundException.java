package cn.huntercat.lieshou.framework.approval.dto;

/** 审批业务异常 */
public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) { super(message); }
}
