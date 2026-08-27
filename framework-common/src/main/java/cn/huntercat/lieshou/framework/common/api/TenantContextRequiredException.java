package cn.huntercat.lieshou.framework.common.api;

/**
 * 租户上下文缺失 / 非法（L2-1 · Bottom-Up）.
 *
 * <p>下沉自 approval / crm / finance / inventory / iot 五个服务的同名重复定义： gateway 从 JWT {@code tid} claim 注入
 * {@code X-Tenant-Id} header，业务端点缺失 / 空 / 非数字 → 抛本异常，由全局处理器映射为 <b>401 UNAUTHORIZED</b> （业务数据不存在"无租户
 * = 平台"的放行路径）。
 */
public class TenantContextRequiredException extends BaseException {

  public TenantContextRequiredException() {
    super(
        ErrorCode.TENANT_CONTEXT_REQUIRED,
        "X-Tenant-Id header is required for tenant-scoped business endpoints");
  }

  public TenantContextRequiredException(String message) {
    super(ErrorCode.TENANT_CONTEXT_REQUIRED, message);
  }
}
