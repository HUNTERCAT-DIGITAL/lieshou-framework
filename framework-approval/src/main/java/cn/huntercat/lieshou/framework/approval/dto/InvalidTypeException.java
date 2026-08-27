package cn.huntercat.lieshou.framework.approval.dto;

/** 审批业务异常 */
public class InvalidTypeException extends RuntimeException {
  public InvalidTypeException(String message) { super(message); }
}
