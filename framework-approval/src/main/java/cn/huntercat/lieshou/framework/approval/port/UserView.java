package cn.huntercat.lieshou.framework.approval.port;

import java.util.List;

/** 审批需要的用户视图（端口 DTO） */
public record UserView(
    Long id, String username, String displayName, String email, String status, List<String> roles) {}
