package cn.huntercat.lieshou.framework.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 租户 / 用户请求头解析工具（Bottom-Up 抽象）.
 *
 * <p>自 user / approval / crm 各服务内联的同源解析逻辑下沉：gateway 从 JWT 注入 {@code X-Tenant-Id} / {@code
 * X-User-Id}，业务端点统一经此解析；缺失 / 非法返回 {@code null}（= 平台上下文，由业务自行决定是否拒绝）。
 *
 * <p>与 {@link cn.huntercat.lieshou.framework.common.api.TenantContextRequiredException}
 * 配合：需要强制租户的端点，解析结果为 null 时抛该异常（→ 401 TENANT_CONTEXT_REQUIRED）。
 */
public final class TenantHeaders {

  public static final String HDR_TENANT_ID = "X-Tenant-Id";
  public static final String HDR_USER_ID = "X-User-Id";

  private TenantHeaders() {}

  /** 当前请求租户 id；缺失/非法 → null */
  public static Long tenantId(HttpServletRequest req) {
    return parseLong(req.getHeader(HDR_TENANT_ID));
  }

  /** 当前请求用户 id；缺失/非法 → null */
  public static Long userId(HttpServletRequest req) {
    return parseLong(req.getHeader(HDR_USER_ID));
  }

  /** 解析数值 header（空/非法 → null） */
  public static Long parseLong(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
