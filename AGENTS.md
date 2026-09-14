# 项目记忆

> 由 pi 的 project-memory 扩展自动创建，与人类维护者共同维护。
> 记录关键事实、决策与约定；避免流水账。

## 项目身份
- 名称: lieshou-framework（猎手云核心框架）
- 类型: Java 核心能力框架库（业务逻辑唯一源 · 上游同源）
- 仓库: https://gitlab.ts.huntercat.cn/huntercat-digital/lieshou-framework（main 分支 · Apache-2.0 · 已发 v0.3.2）
- 发布点: tag `v0.3.2`（最新）/ `v0.3.1` / `v0.3.0` / `release-2026-09`（均 protected）· CI: `.gitlab-ci.yml`（runner tag `java,wqc`，`mvn -B -ntp verify` + 版本防漂移门）
- **制品分发: 内网 Nexus `https://nexus.ts.huntercat.cn`**（运维/接入文档 `/srv/nexus/README.md`，客户端一键接入 `client-setup.sh`）
  - 发布: `deploy` stage，**仅语义化 tag `vX.Y.Z`** 触发（`mvn deploy` → `maven-releases`）；先校验 tag==pom 版本；release 仓**禁止重发**
  - 消费: `~/.m2/settings.xml` mirror → `maven-public`，无需 `<relativePath>`；含 `-sources.jar`
  - 凭证: 项目 CI 变量 `NEXUS_DEPLOY_USER` / `NEXUS_DEPLOY_PASS`（protected+masked），**不落仓库**
- 技术栈: Java 21 · Spring Boot 3.5.7 · Maven 多模块 · jjwt 0.12.6 · Spring Data JPA · Spotless(googleJavaFormat)

## 架构速览
- 定位：三套产品线（lieshou-boot 单体 / lieshou-cloud 微服务 / lieshou-cloud-pro 商业）后端的**唯一业务逻辑源**；各端只留 Controller + 装配 + 启动。
- 十模块（依赖方向自下而上）：`framework-jwt`（无 Web 依赖，servlet/webflux 通用）/ `framework-i18n`（多语言门面 + 自动装配）/ `framework-ai`（AI 端口，无框架依赖）→ `framework-common`（异常/错误码/租户头/审计/权限注解，依赖 i18n）→ `framework-domain`（JPA 实体 + Repository）→ `framework-service`（用户/租户/角色/邀请码/通知/验证码/审计领域服务）/ `framework-approval`（审批状态机 + 端口）/ `framework-rules`（规则引擎实体 + Repository）→ `framework-auth`（认证业务 + UserAuthPort 端口）；另 `framework-parent` 为消费方 parent/BOM（仅 pom，无代码）。
- 端口-适配器模式：framework 只定义端口（UserAuthPort / CodeSender / PermissionChecker / AuditRecorder / UserQueryPort / NotifierPort），单体本地 Adapter / 微服务 Feign Client 装配。

## 关键约定
- 构造器注入优于字段注入；尽量 `final`；错误抛 `BaseException`（`{error, message}` 契约 + HttpStatus），由 `GlobalExceptionHandler` 统一转响应。
- 多租户：共享表 + tenant_id 行级隔离；租户上下文经 gateway 注入的 `X-Tenant-Id` / `X-User-Id` 头解析。
- 代码风格：Spotless googleJavaFormat（verify 阶段 check，`mvn spotless:apply` 一键格式化）；包名 `cn.huntercat.lieshou.framework.*`。
- Commit：Conventional Commits；业务改动 → 发新版本 → 消费方 bump。

## 当前阶段
- **v0.3.2 已发布**（2026-09-14 · 十模块 · 130 测试全过 · `mvn -B -ntp verify` 即门，GitLab pipeline 强制）。
  制品入 Nexus `maven-releases`：11 个坐标（9 个 `framework-*` jar + `framework-parent` / `lieshou-framework` 两个 pom）
  **+ 9 个 `-sources.jar`**。v0.3.1 是首个入 Nexus 的版本（缺 sources）；v0.3.0 从未分发。
- **上游仍无 schema 属主权**：`framework-domain` 拥有 7 张共享表实体（`users`/`tenants`/`roles`/`audit_logs`/`notifications`/`verification_codes`/`tenant_invites`），
  但上游 **0 个 SQL 迁移**、下游 **116 个**，且这 7 张表的 `CREATE TABLE` 全在 `user-services` 的 `V1`–`V16`。
  实测耦合：上游 `47435d9`（`User` 加 `avatarUrl`）→ 迁移 `V15__add_user_avatar_url.sql` 落在 `user-services`。
  后果：各服务跑不同 framework 版本时，同一张表被两套实体定义操作而**无机器守卫**。
- 历史决策原文待补：索引与摘要见 `docs/adr/README.md`（本仓不发明决策内容）。
- ADR-0044 上收进行中：阶段 1-3（角色/邀请码 → 租户 → 用户生命周期）已完成并入库；README 已同步。
- 认证扩展（ADR-0023）：登录/验证码/注册/重置密码/切换租户/多租户选项已同源；默认租户可配置（`auth.default-tenant-code`，缺省 huntercat）。

## 待办
- [x] v0.3.0 / v0.3.1 / v0.3.2 已发布（2026-09-14）
- [x] 制品分发（2026-09-14）：Nexus `https://nexus.ts.huntercat.cn`，CI `deploy` stage 仅语义化 tag 触发；
      runner 已 map `nexus.ts.huntercat.cn` + 挂载卷自带 `caddy-root.crt`（job 里 keytool 导入）；
      凭证 = 项目 CI 变量 `NEXUS_DEPLOY_USER`/`NEXUS_DEPLOY_PASS`（protected+masked）
- [x] sources jar 发布（0.3.2 起）：9 个 `framework-*` 各带 `-sources.jar`
- [ ] ⚠️ **`framework-parent` 的 `lieshou-framework.version` 仍是字面量 property**：消费方覆写即可让
      parent 与 `framework-*` 解析成不同版本。以前因无制品会**解析失败（意外保护）**，
      **现在有 Nexus 了该混搭会真的构建成功** → 需拆 `huntercat-build-parent`(pluginManagement) +
      `lieshou-framework-bom`(dependencyManagement, `import` scope)
- [ ] 共享表迁移上收进 `framework-domain` + 实体↔schema 校验门（Testcontainers + `hbm2ddl validate`，建议落 ADR-0045）
- [ ] 消费方切 Nexus：删 `services/*/framework` submodule、清 `<relativePath>`、上门禁（禁内联上游源码 / 禁 submodule 残留 / 交付仓内单版本）
- [ ] ⚠️ 消费方适配（2026-09 变更，见 CHANGELOG）：artifactId 改名 `lieshou-framework`、`UserAuthPort` 异常契约（业务否定抛 BaseException）、密码策略 ≥8 位+字母数字 —— lieshou-boot / auth-services / user-services 等需同步
- [ ] 单体 `lieshou-standalone` 的 framework pin 停在 `8993195`（v0.1.0-23）· **落后 36 提交**，跨 v0.2.0 三处破坏性变更 + v0.3.0 parent 重构 —— 需独立适配（10 个微服务仓已齐步 v0.3.x）
- [ ] runner 单并发代价：`/srv/gitlab-runner/config/config.toml` `concurrent = 1` 挂 83 个项目，
      实测 v0.3.2 的 verify **排队 15 分钟**（实跑 106s）、MR !6 排队 691s —— 调参前须先解决
      `[runners.docker]` 无 `memory` 上限 + 共享 m2 卷并发写风险

## 关键决策
- 2026-09: **制品发布到内网 Nexus**——`deploy` stage 仅语义化 tag `vX.Y.Z` 触发（里程碑锚点 tag `release-*` / `<主题>-YYYY-MM` 不触发，否则会被 tag↔版本校验判不一致而误红）；先校验 `$CI_COMMIT_TAG == v<pom version>`；`maven-releases` **禁止重发**（不可变性的强制点）。
- 2026-09: **消费方一致性单位 = 交付仓**（非全局单版本）：一个交付仓内所有服务同 framework 版本；交付仓之间按发布点各自 pin（与 `SUPPLY_CHAIN.md` 的「发布点=集合」一致）。
- 2026-09: `UserAuthPort` 异常契约——业务否定抛 BaseException（透传错误码），依赖故障抛其他异常（→ 503 SERVICE_UNAVAILABLE）
- 2026-09: 密码策略统一 ≥8 位且含字母+数字（UserService.create/update/changePassword + TenantRegistrationService，错误码 WEAK_PASSWORD）
- 2026-09: `@Audited(resourceId)` SpEL（#id/#p0，回退首个数值参数）；通知列表分页下推 SQL（未读优先 + Pageable）
- 2026-09: artifactId `lieShou-framework` → `lieshou-framework`（与目录/命名规范对齐，破坏性变更已声明 CHANGELOG）
- 2026-09: 依赖故障 ≠ 业务否定——`UserAuthPort` 返回 null = 不存在（业务否定），抛异常 = 上游不可用 → 503 `SERVICE_UNAVAILABLE`（不再误报 USER_NOT_FOUND）；动作类异常带 cause + warn 日志。
- 2026-09: 默认租户编码可配置化（`auth.default-tenant-code`）——构造器注入 `@Value`（修复 @InjectMocks 回归：字段注入导致测试中为 null）。
- 2026-09: 内置 `DevCodeSender`（@ConditionalOnMissingBean，与 LoggingAuditRecorder 同模式）——消费方不注册 CodeSender 时 VerificationService 也能启动。
- 2026-09: 测试包名与主代码对齐（`framework.auth`），避免 surefire 残留旧编译产物跑旧测试。
- 2026-08: ADR-0044 —— 两端 Controller 内联业务上收 framework-service（用户/租户/角色/邀请码/通知），薄壳只留 Controller + 装配。
- 2026-08: ADR-0023/0022 —— 验证码登录/开放注册/邀请注册；多租户 JWT 带 tid/tcode；租户版别 Edition（GENERIC/LAYER/LEGALMIND/ZHIYE/JMZZ）。
- 2026-08: 端口-适配器模式（UserAuthPort 等），单体本地实现 / 微服务 Feign 实现，业务只依赖端口。
