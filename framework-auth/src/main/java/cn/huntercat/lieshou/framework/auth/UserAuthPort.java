package cn.huntercat.lieshou.framework.auth;

import cn.huntercat.lieshou.framework.auth.dto.UserAuthView;
import java.util.Map;

/**
 * auth → user 本地调用契约（单体重组：原 Feign 接口去掉 {@code @FeignClient}，
 * 由 {@link UserAuthAdapter} 直接调用 user 模块 Repository/Service）。
 */
public interface UserAuthPort {

  /** 按租户 + username 查鉴权视图 */
  UserAuthView findByTenantAndUsername(String tenantCode, String username);

  /** 登录成功后回写 last_login_at（失败由调用方吞掉，不影响登录主流程） */
  void markLastLogin(Long id);

  /** 发送验证码 */
  void sendVerificationCode(Map<String, String> body);

  /** 校验验证码（一次性，校验后作废） */
  void verifyVerificationCode(Map<String, String> body);

  /** 按手机号查鉴权视图 */
  UserAuthView findByPhone(String phone);

  /** 按邮箱查鉴权视图 */
  UserAuthView findByEmail(String email);

  /** 创建用户（注册用；body: username/displayName/password/inviteCode/tenantCode/phone/email） */
  Map<String, Object> createUser(Map<String, String> body);

  /** 重置密码（body: {password}） */
  void updateUserPassword(Long id, Map<String, String> body);

  /** 按用户名查可登录租户选项（多租户登录前 · tenantOptions 端点） */
  java.util.List<java.util.Map<String, Object>> tenantOptions(String username);
}
