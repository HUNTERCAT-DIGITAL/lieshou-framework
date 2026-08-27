package cn.huntercat.lieshou.framework.approval.port;

import java.util.List;

/** 审批 → 用户查询端口（单体本地实现 / 微服务 Feign 实现） */
public interface UserQueryPort {

  /** 租户用户列表（含 roles code 数组；自动选审批人用） */
  List<UserView> listTenantUsers(String tenantId);

  /** 单个用户（通知收件人邮箱用） */
  UserView getUserById(Long id, String tenantId);
}
