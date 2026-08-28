package cn.huntercat.lieshou.framework.common.api;

import org.springframework.http.HttpStatus;

/**
 * 通用错误码（L2-1 · Bottom-Up）.
 *
 * <p>与前端 {@code @lieshoucloud/api-client} 的错误体契约对齐： {@code { error: <机器可读码>, message: <人类可读信息> }}。
 * 通用错误码集中在此；业务域特有错误码（如 {@code ALREADY_DECIDED}）由 {@link BaseException} 携带任意字符串码，无需进枚举。
 */
public enum ErrorCode {

  /** 参数非法 / 请求无法解析（400） */
  BAD_REQUEST(HttpStatus.BAD_REQUEST),

  /** 参数校验失败（@Valid 字段级 · 400） */
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

  /** 未认证 / 凭证无效（401） */
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

  /** 租户上下文缺失 / 非法（gateway 未注入 X-Tenant-Id · 401） */
  TENANT_CONTEXT_REQUIRED(HttpStatus.UNAUTHORIZED),

  /** 无权限（403） */
  FORBIDDEN(HttpStatus.FORBIDDEN),

  /** 资源不存在（404） */
  NOT_FOUND(HttpStatus.NOT_FOUND),

  /** 状态机冲突 / 业务规则冲突（409） */
  CONFLICT(HttpStatus.CONFLICT),

  /** 服务器内部错误（500 · 不向客户端泄露堆栈） */
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

  /** 上游依赖不可用（user-service 网络故障等 · 503 · 与业务否定区分，避免误报） */
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

  private final HttpStatus httpStatus;

  ErrorCode(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
