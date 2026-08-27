package cn.huntercat.lieshou.framework.common.api;

import org.springframework.http.HttpStatus;

/**
 * 业务异常基类（L2-1 · Bottom-Up）.
 *
 * <p>携带错误契约的两要素：{@code errorCode}（机器可读码，对应前端解析的 {@code error} 字段）与 {@code httpStatus}。由 {@code
 * GlobalExceptionHandler} 统一映射为 {@code { error, message }} 契约体；业务服务抛自定义异常时继承本类， 或在简单场景直接 {@code
 * throw new BaseException(ErrorCode.XXX, message)}。
 */
public class BaseException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus httpStatus;

  /** 业务域特有错误码（如 ALREADY_DECIDED / INSUFFICIENT_STOCK） */
  public BaseException(String errorCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
  }

  /** 通用错误码便捷构造 */
  public BaseException(ErrorCode code, String message) {
    this(code.name(), code.httpStatus(), message);
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
