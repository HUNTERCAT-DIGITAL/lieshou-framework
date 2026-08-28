# 项目记忆

> 由 pi 的 project-memory 扩展自动创建，与人类维护者共同维护。
> 记录关键事实、决策与约定；避免流水账。

## 项目身份
- 名称: lieshou-framework（猎手云核心框架）
- 类型: Java 核心能力框架库（业务逻辑唯一源 · 上游同源）
- 仓库: https://github.com/HUNTERCAT-DIGITAL/lieshou-framework（main 分支 · Apache-2.0 · 已发 v0.1.0）
- 技术栈: Java 21 · Spring Boot 3.5.7 · Maven 多模块 · jjwt 0.12.6 · Spring Data JPA · Spotless(googleJavaFormat)

## 架构速览
- 定位：三套产品线（lieshou-boot 单体 / lieshou-cloud 微服务 / lieshou-cloud-pro 商业）后端的**唯一业务逻辑源**；各端只留 Controller + 装配 + 启动。
- 六模块（依赖方向自下而上）：`framework-jwt`（无 Web 依赖，servlet/webflux 通用）→ `framework-common`（异常/错误码/租户头/审计/权限注解）→ `framework-domain`（JPA 实体 + Repository）→ `framework-service`（用户/租户/角色/邀请码/通知/验证码/审计领域服务）→ `framework-approval`（审批状态机 + 端口）→ `framework-auth`（认证业务 + UserAuthPort 端口）。
- 端口-适配器模式：framework 只定义端口（UserAuthPort / CodeSender / PermissionChecker / AuditRecorder / UserQueryPort / NotifierPort），单体本地 Adapter / 微服务 Feign Client 装配。

## 关键约定
- 构造器注入优于字段注入；尽量 `final`；错误抛 `BaseException`（`{error, message}` 契约 + HttpStatus），由 `GlobalExceptionHandler` 统一转响应。
- 多租户：共享表 + tenant_id 行级隔离；租户上下文经 gateway 注入的 `X-Tenant-Id` / `X-User-Id` 头解析。
- 代码风格：Spotless googleJavaFormat（verify 阶段 check，`mvn spotless:apply` 一键格式化）；包名 `cn.huntercat.lieshou.framework.*`。
- Commit：Conventional Commits；业务改动 → 发新版本 → 消费方 bump。

## 当前阶段
- v0.1.0 已 RELEASE（六模块 · 57 测试全过，`mvn clean verify` 验证）。
- ADR-0044 上收进行中：阶段 1-3（角色/邀请码 → 租户 → 用户生命周期）已完成并入库；README 已同步。
- 认证扩展（ADR-0023）：登录/验证码/注册/重置密码/切换租户/多租户选项已同源；默认租户可配置（`auth.default-tenant-code`，缺省 huntercat）。

## 待办
- [ ] 规划 0.2.0 发版（v0.1.0 后已积压 ADR-0044 上收 + 认证扩展等大量未发版功能）
- [ ] `CodeSender` 缺 dev 实现（javadoc 提到 DevCodeSender 但类不存在，消费方需自备实现，否则 VerificationService 启动失败）
- [ ] `AuditedAspect` 组装 AuditEvent 时 resourceId 恒为 null（半成品）
- [ ] 测试包名不一致：`framework-auth` 测试在 `auth.service` 包（与主代码 `auth` 包不符）
- [ ] `NotificationService.list` 全量拉取后内存分页（大数据量隐患）；Repository 排序与内存排序矛盾

## 关键决策
- 2026-09: 默认租户编码可配置化（`auth.default-tenant-code`）——构造器注入 `@Value`（修复 @InjectMocks 回归：字段注入导致测试中为 null）。
- 2026-08: ADR-0044 —— 两端 Controller 内联业务上收 framework-service（用户/租户/角色/邀请码/通知），薄壳只留 Controller + 装配。
- 2026-08: ADR-0023/0022 —— 验证码登录/开放注册/邀请注册；多租户 JWT 带 tid/tcode；租户版别 Edition（GENERIC/LAYER/LEGALMIND/ZHIYE/JMZZ）。
- 2026-08: 端口-适配器模式（UserAuthPort 等），单体本地实现 / 微服务 Feign 实现，业务只依赖端口。
