package cn.huntercat.lieshou.framework.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 全局异常处理器（L2-1 · Bottom-Up）.
 *
 * <p>统一异常体契约（与 {@code @lieshoucloud/api-client} 对齐）：
 *
 * <pre>{@code { "error": "<机器可读码>", "message": "<人类可读信息>" }}</pre>
 *
 * <p>覆盖：{@link BaseException}（含各业务子类）→ 异常自带码与状态；参数校验 （{@code @Valid} 字段级）→ {@code
 * VALIDATION_FAILED}；JDK 语义异常 （NoSuchElement/IllegalState/IllegalArgument）→ 404/409/400；兜底 → 500
 * （仅记日志，不向客户端泄露堆栈）。
 *
 * <p>替代各服务自维护的 {@code *ExceptionHandler}（approval / crm / finance / inventory / iot），业务服务只需依赖
 * {@code lieshoucloud-common} 并继承 {@link BaseException}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** 业务异常：码与状态由异常自带（保持契约码稳定，如 ALREADY_DECIDED / INSUFFICIENT_STOCK） */
  @ExceptionHandler(BaseException.class)
  public ResponseEntity<Map<String, String>> onBaseException(BaseException e) {
    return body(e.httpStatus(), e.errorCode(), e.getMessage());
  }

  /**
   * @Valid 字段级校验失败 → 400 VALIDATION_FAILED（聚合首个字段错误，便于定位）
   */
  @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
  public ResponseEntity<Map<String, String>> onValidation(BindException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(FieldError::getDefaultMessage)
            .orElse("请求参数校验失败");
    return body(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
  }

  /** 资源不存在（各服务通用的存在性语义，如跨租户访问不泄露存在性）→ 404 */
  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Map<String, String>> onNotFound(NoSuchElementException e) {
    return body(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, e.getMessage());
  }

  /** 业务规则冲突（已认领 / 状态机冲突 / 不可转化等）→ 409 BUSINESS_CONFLICT（契约码稳定） */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<Map<String, String>> onConflict(IllegalStateException e) {
    return body(HttpStatus.CONFLICT, "BUSINESS_CONFLICT", e.getMessage());
  }

  /** 非法参数 / 请求体不可读 / 方法不支持 → 400 */
  @ExceptionHandler({
    IllegalArgumentException.class,
    HttpMessageNotReadableException.class,
    HttpRequestMethodNotSupportedException.class
  })
  public ResponseEntity<Map<String, String>> onBadRequest(Exception e) {
    return body(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, e.getMessage());
  }

  /** 唯一键冲突（deviceKey 全局 UNIQUE 等竞态兜底）→ 409 */
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> onDataConflict(
      org.springframework.dao.DataIntegrityViolationException e) {
    return body(HttpStatus.CONFLICT, "DATA_CONFLICT", "数据冲突，请检查唯一性约束后重试");
  }

  /** 兜底：未知异常 → 500，仅记日志不泄露堆栈 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> onUnexpected(Exception e) {
    log.error("Unhandled exception", e);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试");
  }

  private static ResponseEntity<Map<String, String>> body(
      HttpStatus status, ErrorCode code, String message) {
    return body(status, code.name(), message);
  }

  private static ResponseEntity<Map<String, String>> body(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
  }
}
