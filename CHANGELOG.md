# Changelog · lieshou-framework

> **破坏性变更声明（ADR-0002）**：framework 是 5+ 底座服务 + lieshou-boot 共用的**上游同源唯一**，
> 公共 API 变更（包移动 / 类删除 / 签名变化）必须在此记录，消费方升级前对照评估适配范围。
> 格式：`## <版本/区间>` → `### 破坏性变更` / `### 新增` / `### 修复`。

## Unreleased（main，v0.1.0 之后）

### 破坏性变更
- **`UserAuthView` 包移动**（ADR-0044 上收期间）
  - 旧：`cn.huntercat.lieshou.framework.auth.dto.UserAuthView`
  - 新：`cn.huntercat.lieshou.framework.common.dto.UserAuthView`
  - 适配：消费方 import 改为新路径（实测 auth-services 仅 1 行 import）
  - 说明：未保留 deprecated 桥接——移动后旧路径已无消费者，桥接收益低；按 ADR-0002「显式声明」替代

### 新增
- `framework-service` 上收业务（ADR-0044）：`NotificationService` / `VerificationService` / `AuditService` / `RoleService` / `TenantInviteService` / `TenantRegistrationService` / `TenantService` / `UserService`（含 `changePassword` 自助改密码、`tenantOptions`）
- `framework-auth`：`SwitchTenantRequest` 组件加 `@NotBlank` 校验注解（**签名不变**）；默认租户编码可配置化 `auth.default-tenant-code`；注册验证码可选（开放注册）

### 修复
- `framework-auth`：默认租户编码改构造器注入（修复 `@InjectMocks` 测试回归）

## v0.1.0（2026-08）

- 初始发布：`framework-jwt` / `framework-common` / `framework-domain` / `framework-service` / `framework-approval` / `framework-auth` 六模块
- parent version 固化 0.1.0（RELEASE）
