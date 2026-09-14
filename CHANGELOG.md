# Changelog · lieshou-framework

> **破坏性变更声明（ADR-0002）**：framework 是 5+ 底座服务 + lieshou-boot 共用的**上游同源唯一**，
> 公共 API 变更（包移动 / 类删除 / 签名变化）必须在此记录，消费方升级前对照评估适配范围。
> 格式：`## <版本/区间>` → `### 破坏性变更` / `### 新增` / `### 修复`。

## 0.3.0（2026-09-14）

### 破坏性变更
- **版本号 `0.2.0 → 0.3.0`**（根 pom + 9 个子模块的 parent 引用 + framework-parent，共 12 处字面量已同步）
  - 适配：消费方 `<parent>` 的 `<version>` 改为 `0.3.0`；`framework-*` 依赖**删除**自带 `<version>`
    （版本由 framework-parent 的 BOM 提供）
- **旧 parent 坐标 `lieshoucloud-services-open`（Gen1）/ `lieshoucloud-services`（Gen2）正式废弃**
  - 适配：10 个服务仓删除自己的 `services/pom.xml`，`<parent>` 改指
    `cn.huntercat:framework-parent:0.3.0`，`<relativePath>` 用
    `../../framework/framework-parent/pom.xml`
- `framework-parent` 剔除两条死条目 `lieshoucloud-jwt-support` / `lieshoucloud-common`
  - 适配：无（两者均无任何模块依赖、无 java import，经 10 仓并集核对确认）

### 新增
- **`framework-parent` 模块**：lieshou-cloud 微服务统一 parent/BOM。
  消费方（10 个服务仓）`<parent>` 引用方式：

  ```xml
  <parent>
    <groupId>cn.huntercat</groupId>
    <artifactId>framework-parent</artifactId>
    <version>0.3.0</version>
    <relativePath>../../framework/framework-parent/pom.xml</relativePath>
  </parent>
  ```

  该模块合并了原 `lieshoucloud-services-open`（Gen1，5 仓）与 `lieshoucloud-services`（Gen2，5 仓）
  两份同名 parent；`framework-*` 版本用字面量 property `lieshou-framework.version` 表达
  （不要改成 `project.version`——继承后的 dependencyManagement 在消费方上下文插值）。

### 修复
- 根 pom `<dependencyManagement>` 补上缺失的 `framework-i18n`（原先 9 个模块只管理了 8 个）
- 根 pom 的 spotless 版本改用 `${spotless.version}` property（与 framework-parent 一致，消除漂移向量）
- 删除 `framework-common` 里对 `framework-i18n` 的冗余硬编码版本（由父 dependencyManagement 提供）
- `.gitlab-ci.yml` 增加防漂移校验：两份 `framework-*` 清单同序等长 + 三处版本号一致

## 0.2.0（2026-09-13）

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
