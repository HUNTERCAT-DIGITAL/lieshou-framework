package cn.huntercat.lieshou.framework.common.security;

import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.huntercat.lieshou.framework.common.api.BaseException;
import java.util.concurrent.atomic.AtomicBoolean;

/** RequiresPermissionAspect 单测（L2-1 · 权限注解收尾）——AspectJ 手动代理. */
class RequiresPermissionAspectTest {

  static class Target {
    @RequiresPermission("tenant:manage")
    public String manage() {
      return "managed";
    }
  }

  private Target proxy(PermissionChecker checkerOrNull) {
    RequiresPermissionAspect aspect = new RequiresPermissionAspect(provider(checkerOrNull));
    AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
    factory.addAspect(aspect);
    return factory.getProxy();
  }

  @Test
  void grants_whenCheckerAllows() {
    Target p = proxy(code -> true);
    assertThat(p.manage()).isEqualTo("managed");
  }

  @Test
  void denies_withForbiddenWhenCheckerRejects() {
    Target p = proxy(code -> false);
    assertThatThrownBy(p::manage)
        .isInstanceOf(BaseException.class)
        .extracting(e -> ((BaseException) e).errorCode())
        .isEqualTo("FORBIDDEN");
  }

  @Test
  void passesPermissionCodeToChecker() {
    AtomicBoolean called = new AtomicBoolean(false);
    Target p =
        proxy(
            code -> {
              called.set(true);
              assertThat(code).isEqualTo("tenant:manage");
              return true;
            });
    p.manage();
    assertThat(called).isTrue();
  }

  @Test
  void grants_whenNoCheckerRegistered() {
    Target p = proxy(null);
    assertThat(p.manage()).isEqualTo("managed");
  }

  private static ObjectProvider<PermissionChecker> provider(PermissionChecker checkerOrNull) {
    StaticListableBeanFactory bf = new StaticListableBeanFactory();
    if (checkerOrNull != null) {
      bf.addBean("checker", checkerOrNull);
    }
    return bf.getBeanProvider(PermissionChecker.class);
  }
}
