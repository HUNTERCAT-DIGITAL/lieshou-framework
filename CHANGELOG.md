# Changelog · lieshou-framework

> **破坏性变更声明（ADR-0002）**：framework 是 5+ 底座服务 + lieshou-boot 共用的**上游同源唯一**，
> 公共 API 变更（包移动 / 类删除 / 签名变化）必须在此记录，消费方升级前对照评估适配范围。
> 格式：`## <版本/区间>` → `### 破坏性变更` / `### 新增` / `### 修复`。

## Unreleased（main，v0.1.0 之后）

### 破坏性变更
- **artifactId 改名**：`lieShou-framework` → `lieshou-framework`（与仓库目录/命名规范对齐）
  - 适配：消费方 pom parent 引用同步改为 `cn.huntercat:lieshou-framework:<version>`
- **密码策略收紧**（统一 `UserService` / `TenantRegistrationService` / `changePassword`）
  - 旧：≥6 位；新：≥8 位且同时包含字母和数字
  - 影响：注册 / 创建用户 / 改密输入校验；错误码 `INVALID_PASSWORD` → `WEAK_PASSWORD`
- **`UserAuthPort` 异常契约**：业务否定（验证码错误/发送太频繁/用户名占用等）必须抛 `BaseException`（带错误码）；
  依赖故障抛其他 RuntimeException（映射 503 `SERVICE_UNAVAILABLE`）。消费方端口实现需遵循（auth-services / lieshou-boot adapter）
- **`UserAuthView` 包移动**（ADR-0044 上收期间）
  - 旧：`cn.huntercat.lieshou.framework.auth.dto.UserAuthView`
  - 新：`cn.huntercat.lieshou.framework.common.dto.UserAuthView`
  - 适配：消费方 import 改为新路径（实测 auth-services 仅 1 行 import）
  - 说明：未保留 deprecated 桥接——移动后旧路径已无消费者，桥接收益低；按 ADR-0002「显式声明」替代

### 新增
- `framework-service` 上收业务（ADR-0044）：`NotificationService` / `VerificationService` / `AuditService` / `RoleService` / `TenantInviteService` / `TenantRegistrationService` / `TenantService` / `UserService`（含 `changePassword` 自助改密码、`tenantOptions`）
- `framework-auth`：`SwitchTenantRequest` 组件加 `@NotBlank` 校验注解（**签名不变**）；默认租户编码可配置化 `auth.default-tenant-code`；注册验证码可选（开放注册）
- `framework-common`：`ErrorCode.SERVICE_UNAVAILABLE`（503）；`BaseException` 带 cause 构造器；`@Audited(resourceId)` SpEL 支持（`#id` / `#p0`，缺省回退首个数值参数）
- `framework-service`：`DevCodeSender` 默认验证码发送器（`@ConditionalOnMissingBean`，消费方未注册 `CodeSender` 时服务可启动）；通知列表分页下推数据库（未读优先 + `Pageable`，替代全量内存分页）

### 修复
- `framework-auth`：默认租户编码改构造器注入（修复 `@InjectMocks` 测试回归）；依赖故障与业务否定分离（查询端口抛异常 → 503 不再误报 `USER_NOT_FOUND`；动作类异常带 cause + warn 日志）
- `framework-common`：`AuditedAspect` 组装 `AuditEvent` 填充 `resourceId`（此前恒为 null）
- `framework-service`：`RoleService.update` 作用域枚举解析统一错误码 `INVALID_SCOPE`（此前裸 `valueOf` 抛 IllegalArgumentException）；`UserService.update` 未知角色码报 `INVALID_ROLE`（此前静默丢弃）
- 测试包名对齐：`framework.auth.service` → `framework.auth`（消除 surefire 残留旧编译产物隐患）

## v0.1.0（2026-08）

- 初始发布：`framework-jwt` / `framework-common` / `framework-domain` / `framework-service` / `framework-approval` / `framework-auth` 六模块
- parent version 固化 0.1.0（RELEASE）
