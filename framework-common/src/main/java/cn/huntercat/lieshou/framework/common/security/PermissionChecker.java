package cn.huntercat.lieshou.framework.common.security;

/**
 * 权限校验 SPI（L2-1 · Bottom-Up）.
 *
 * <p>由各服务 / 未来 RBAC 实现注册为 bean：基于 JWT {@code permissions} claim、 角色推导或后端权限表判断当前用户是否持有权限码。未注册时
 * {@link RequiresPermissionAspect} 放行并打 warn 日志。
 */
public interface PermissionChecker {

  /** 当前用户是否持有权限码（tenant:manage / legal:use …） */
  boolean hasPermission(String permissionCode);
}
