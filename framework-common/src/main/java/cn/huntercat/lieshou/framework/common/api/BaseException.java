package cn.huntercat.lieshou.framework.common.api;

import org.springframework.http.HttpStatus;

/**
 * 业务异常基类（L2-1 · Bottom-Up）.
 *
 * <p>携带错误契约的两要素：{@code errorCode}（机器可读码，对应前端解析的 {@code error} 字段）与 {@code httpStatus}。由 {@code
 * GlobalExceptionHandler} 统一映射为 {@code { error, message }} 契约体。
 *
 * <p>文案来源（多语言 · 2026-09 S5）：
 * <ul>
 *   <li>新代码：{@code BaseException.i18n(code, "error.xxx.yyy", args)} —— 只写 key + 参数，message 由
 *       framework-i18n 按请求 Locale 本地化（前端与后端共享 lieshou-i18n 数据源）</li>
 *   <li>旧代码：直接 {@code new BaseException(code, message)} —— message 透传（i18nKey 为 null，行为不变）</li>
 * </ul>
 */
public class BaseException extends RuntimeException {

  private final String errorCode;
  private final HttpStatus httpStatus;
  /** i18n key（null = 直接透传 message 的旧用法） */
  private final String i18nKey;
  private final Object[] i18nArgs;

  /** 业务域特有错误码 + i18n key + 参数（多语言推荐用法） */
  public static BaseException i18n(String errorCode, HttpStatus httpStatus, String i18nKey, Object... i18nArgs) {
    return new BaseException(errorCode, httpStatus, i18nKey, i18nArgs);
  }

  /** 通用错误码 + i18n key + 参数（多语言推荐用法） */
  public static BaseException i18n(ErrorCode code, String i18nKey, Object... i18nArgs) {
    return new BaseException(code.name(), code.httpStatus(), i18nKey, i18nArgs);
  }

  /** 通用错误码 + i18n key + 参数 + 根因（内部异常包装场景，保留 cause 供日志排查） */
  public static BaseException i18n(ErrorCode code, String i18nKey, Throwable cause, Object... i18nArgs) {
    BaseException e = new BaseException(code.name(), code.httpStatus(), i18nKey, i18nArgs);
    e.initCause(cause);
    return e;
  }

  /** 业务域特有错误码（如 ALREADY_DECIDED / INSUFFICIENT_STOCK）—— message 直接透传（旧用法） */
  public BaseException(String errorCode, HttpStatus httpStatus, String message) {
    super(message);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.i18nKey = null;
    this.i18nArgs = new Object[0];
  }

  /** 业务域特有错误码 + 根因（依赖故障时保留 cause，便于日志排查） */
  public BaseException(String errorCode, HttpStatus httpStatus, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.i18nKey = null;
    this.i18nArgs = new Object[0];
  }

  /** 通用错误码便捷构造 */
  public BaseException(ErrorCode code, String message) {
    this(code.name(), code.httpStatus(), message);
  }

  /** 通用错误码便捷构造 + 根因（依赖故障用） */
  public BaseException(ErrorCode code, String message, Throwable cause) {
    this(code.name(), code.httpStatus(), message, cause);
  }

  private BaseException(String errorCode, HttpStatus httpStatus, String i18nKey, Object[] i18nArgs) {
    super(i18nKey); // message 暂存 key（日志可读），handler 负责本地化
    this.errorCode = errorCode;
    this.httpStatus = httpStatus;
    this.i18nKey = i18nKey;
    this.i18nArgs = i18nArgs == null ? new Object[0] : i18nArgs;
  }

  public String errorCode() {
    return errorCode;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }

  /** i18n key（null = message 透传模式） */
  public String i18nKey() {
    return i18nKey;
  }

  /** i18n 插值参数 */
  public Object[] i18nArgs() {
    return i18nArgs;
  }
}
