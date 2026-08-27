package cn.huntercat.lieshou.framework.approval.port;

import cn.huntercat.lieshou.framework.domain.ApprovalRequest;

/** 审批通知端口（邮件/站内信 · 各端实现） */
public interface NotifierPort {

  void notifyApprover(Long tenantId, ApprovalRequest request);

  void notifyRequester(Long tenantId, ApprovalRequest request, String result);
}
