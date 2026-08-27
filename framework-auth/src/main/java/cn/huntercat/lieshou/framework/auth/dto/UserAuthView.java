package cn.huntercat.lieshou.framework.auth.dto;

import java.util.List;

/**
 * user-service 暴露给 auth-service 的鉴权视图（service-to-service，含 passwordHash）.
 *
 * <p>仅 {@code /api/users/auth/**} 端点会返回此视图；公开端点返不含 passwordHash 的 {@code User} 实体本身或 UserView.
 *
 * <p>Phase 5 SpringDoc: 暴露在 OpenAPI spec 中, 但通过 description 标注服务间调用专用.
 *
 * <p>Phase 6（ADR-0021）: 新增 {@code status}，供 auth-service 在登录时校验账户是否可用.
 *
 * <p>Phase 8（ADR-0022）: 新增 {@code tenantId} / {@code tenantCode}，JWT 带租户维度.
 *
 * <p>Phase 10（ADR-0035）: 新增 {@code tenantName} / {@code tenantEdition}，登录时返回租户品牌/版别信息.
 */
public record UserAuthView(
    Long id,
    Long tenantId,
    String tenantCode,
    String tenantName,
    String tenantEdition,
    String username,
    String displayName,
    String passwordHash,
    List<String> roles,
    String status) {}
