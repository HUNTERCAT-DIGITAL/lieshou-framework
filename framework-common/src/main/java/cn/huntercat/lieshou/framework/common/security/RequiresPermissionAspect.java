package cn.huntercat.lieshou.framework.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import cn.huntercat.lieshou.framework.common.api.ErrorCode;

/**
 * 权限校验切面（L2-1 · Bottom-Up）.
 *
 * <p>{@code @RequiresPermission(code)} 方法执行前调用 {@link PermissionChecker}： 无权限 → 抛 {@link
 * BaseException}（403 FORBIDDEN，由全局处理器转契约体）。 未注册 checker → 放行并打 warn 日志（兼容当前无权限模型的存量服务）。
 */
@Aspect
@Component
public class RequiresPermissionAspect {

  private static final Logger log = LoggerFactory.getLogger(RequiresPermissionAspect.class);

  private final ObjectProvider<PermissionChecker> checkerProvider;

  public RequiresPermissionAspect(ObjectProvider<PermissionChecker> checkerProvider) {
    this.checkerProvider = checkerProvider;
  }

  @Around("@annotation(requiresPermission)")
  public Object check(ProceedingJoinPoint pjp, RequiresPermission requiresPermission)
      throws Throwable {
    PermissionChecker checker = checkerProvider.getIfAvailable();
    if (checker == null) {
      log.warn(
          "PermissionChecker 未注册，@RequiresPermission({}) 放行（规范先立、保护后补）",
          requiresPermission.value());
      return pjp.proceed();
    }
    if (!checker.hasPermission(requiresPermission.value())) {
      throw new BaseException(ErrorCode.FORBIDDEN, "无权限执行该操作");
    }
    return pjp.proceed();
  }
}
