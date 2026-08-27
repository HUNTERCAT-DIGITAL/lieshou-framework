package cn.huntercat.lieshou.framework.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限注解（L2-1 · Bottom-Up）.
 *
 * <p>标注在需要鉴权的 Controller 方法 / 类上，由 {@link RequiresPermissionAspect} 校验： 权限码与前端菜单可见性同源（JWT {@code
 * permissions} claim / 后端 permissions 表， ADR-0024 · 权限码驱动），如 {@code tenant:manage} / {@code
 * legal:use}。
 *
 * <pre>{@code
 * @RequiresPermission("tenant:manage")
 * public ResponseEntity<List<TenantView>> list() { ... }
 * }</pre>
 *
 * <p>未注册 {@link PermissionChecker} 时放行（规范先立、保护后补，不误伤未接入服务）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

  /** 权限码（与 JWT permissions claim / 前端 access.ts 对齐） */
  String value();
}
