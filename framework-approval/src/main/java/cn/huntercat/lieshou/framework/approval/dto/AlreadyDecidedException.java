package cn.huntercat.lieshou.framework.approval.dto;

/** 审批业务异常 */
public class AlreadyDecidedException extends RuntimeException {
  public AlreadyDecidedException(String message) { super(message); }
}
