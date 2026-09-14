package cn.huntercat.lieshou.framework.common.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;
import cn.huntercat.lieshou.framework.i18n.I18nMessages;
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
 * VALIDATION_FAILED}；JDK 语义异常 （NoSuchElement/IllegalState/IllegalArgument）→ 404/409/400；兜底 → 保留
 * Spring 自带 4xx 语义（{@link org.springframework.web.ErrorResponse}），其余 → 500（仅记日志，不向客户端泄露堆栈）。
 *
 * <p>多语言（S5）：{@link BaseException} 携带 i18nKey 时，message 由 {@link I18nMessages} 按请求 Locale 本地化；
 * 容器未装配 framework-i18n（ObjectProvider 降级）或 i18nKey 为 null（旧用法）时原样透传。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ObjectProvider<I18nMessages> i18nProvider;

  public GlobalExceptionHandler(ObjectProvider<I18nMessages> i18nProvider) {
    this.i18nProvider = i18nProvider;
  }

  /** 业务异常：码与状态由异常自带（保持契约码稳定，如 ALREADY_DECIDED / INSUFFICIENT_STOCK） */
  @ExceptionHandler(BaseException.class)
  public ResponseEntity<Map<String, String>> onBaseException(BaseException e) {
    String message = resolveMessage(e);
    return body(e.httpStatus(), e.errorCode(), message);
  }

  /** i18n key → 本地化文案；无 key / 未装配 i18n → 透传原始 message */
  private String resolveMessage(BaseException e) {
    if (e.i18nKey() == null) return e.getMessage();
    I18nMessages i18n = i18nProvider.getIfAvailable();
    return i18n != null ? i18n.get(e.i18nKey(), e.i18nArgs()) : e.i18nKey();
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

  /**
   * 客户端断连 / 异步超时（SSE 等长连接）→ 静默处理，不写错误体。
   *
   * <p>{@link AsyncRequestNotUsableException} 是 {@link java.io.IOException} 子类，原先落到下面 {@code
   * Exception} 兜底：它会对一个 Content-Type 已是 {@code text/event-stream} 的响应写 JSON（{@code
   * Map.of(...)}），Spring 无对应转换器 → {@code HttpMessageNotWritableException} 二次异常，表现为浏览器 EventSource
   * 记一条 500 + 服务端 ERROR 刷屏。此时连接已不可用，没有任何响应可写，直接吞掉。
   */
  @ExceptionHandler({AsyncRequestNotUsableException.class, AsyncRequestTimeoutException.class})
  public void onAsyncRequestUnusable() {
    // 故意不记 error 级日志：客户端断连属正常现象，避免日志刷屏
  }

  /** 兜底：保留 Spring 自带 4xx 状态码；其余未知异常 → 500，仅记日志不泄露堆栈 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> onUnexpected(
      Exception e, HttpServletResponse response) {
    // 响应已提交（流式/SSE 已发头）→ 不能再写错误体，否则触发二次异常；连接已不可用，静默返回
    if (response.isCommitted()) {
      return null;
    }
    // Spring MVC 自带语义异常（ResponseStatusException / NoResourceFound / 缺参等）实现 ErrorResponse：
    // 先尊重其状态码，避免被本兜底统一成 500（跨租户 403 → 500 会让前端误判为服务端故障）
    if (e instanceof org.springframework.web.ErrorResponse er
        && er.getStatusCode().is4xxClientError()) {
      log.warn("业务请求被拒绝：{} {}", er.getStatusCode().value(), e.getMessage());
      return body(er.getStatusCode(), errorCodeOf(er.getStatusCode()), e.getMessage());
    }
    log.error("Unhandled exception", e);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "服务器内部错误，请稍后重试");
  }

  /** 4xx 状态码 → 机器可读码（沿用既有 ErrorCode 常量；缺失即回退 HttpStatus 名） */
  private static String errorCodeOf(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> ErrorCode.BAD_REQUEST.name();
      case 401 -> ErrorCode.UNAUTHORIZED.name();
      case 403 -> ErrorCode.FORBIDDEN.name();
      case 404 -> ErrorCode.NOT_FOUND.name();
      case 409 -> ErrorCode.CONFLICT.name();
      default -> status instanceof HttpStatus hs ? hs.name() : String.valueOf(status.value());
    };
  }

  private static ResponseEntity<Map<String, String>> body(
      HttpStatusCode status, ErrorCode code, String message) {
    return body(status, code.name(), message);
  }

  private static ResponseEntity<Map<String, String>> body(
      HttpStatusCode status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
  }
}
