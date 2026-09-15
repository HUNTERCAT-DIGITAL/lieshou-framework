# lieshou-framework · 猎手云核心框架

> 猎手云产品线的**核心能力框架**（上游同源唯一）：认证 / JWT / 统一异常 / 审计 / 权限 / 领域模型 / 审批。
> **lieshou-boot（单体）与 lieshou-cloud（微服务）共用的薄壳底座**——业务逻辑只在此维护，两端装配引用。
> **v0.3.3（RELEASE，2026-09-15）** · 十模块 · 135 测试全过

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-green" alt="Spring Boot 3.5"/>
  <img src="https://img.shields.io/badge/Version-0.3.3-blue" alt="v0.3.3"/>
  <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen" alt="Apache-2.0"/>
</p>

## 设计哲学（借鉴 Spring 分层）

```
Spring Framework  →  lieshou-framework  核心能力层（业务逻辑唯一源）
Spring Boot       →  lieshou-boot       单体快速启动（Framework + 装配）
Spring Cloud      →  lieshou-cloud      微服务/分布式（Framework + 服务发现/网关）
```

**规则**：业务逻辑（Service / 端口 / DTO / 领域模型）只进 Framework；各端只留 Controller + 装配 + 启动。

## 模块（十模块 · v0.3.3）

| 模块 | 内容 | 依赖 |
| --- | --- | --- |
| **framework-parent** | lieshou-cloud 微服务统一 parent/BOM（10 个服务仓共用，无代码） | 无（仅 pom） |
| **framework-jwt** | JWT 签发/解析/密钥（JwtSupport）+ 自动装配 | 无 Web 依赖（servlet/webflux 通用） |
| **framework-common** | 统一异常 / ErrorCode / 租户上下文 / 审计注解 / 权限注解 | servlet 栈服务共用 |
| **framework-domain** | 领域模型（JPA 实体 + Repository）：用户/租户/角色/验证码/审计/通知 | spring-data-jpa |
| **framework-service** | 领域服务：用户/租户/角色/邀请码/通知/验证码/审计生命周期（ADR-0044 上收）+ CodeSender 端口 | domain + common |
| **framework-approval** | 审批业务：ApprovalService 状态机 + 审批实体 + UserQueryPort/NotifierPort | domain + common |
| **framework-auth** | 认证业务：AuthService（登录/验证码/注册/重置/切换租户/多租户选项）/ JwtService / UserAuthPort（端口）/ DTO | jwt + security |
| **framework-rules** | 规则引擎：`RuleDefinition` 实体 + Repository（按 domain 查询 / 全量查询） | domain + common |
| **framework-ai** | AI 抽象：`AiModelClient` 端口 + OpenAI 兼容实现 + Mock 降级实现 | 无（仅 slf4j / jackson） |
| **framework-i18n** | 多语言：`I18nMessages` 门面（按 `Accept-Language` 解析，缺省中文）+ 自动装配 | spring-boot-starter-web |

## 端口-适配器模式（单体本地实现 / 微服务 Feign 装配）

```java
// framework 定义端口（业务只依赖端口）
public interface UserAuthPort {
  UserAuthView findByTenantAndUsername(String tenantCode, String username);
  ...
}

// lieshou-boot（单体）实现：本地调用
public class UserAuthAdapter implements UserAuthPort { ... }

// lieshou-cloud（微服务）实现：Feign 装配
public interface UserAuthClient extends UserAuthPort { ... }
```

## 消费方（上游同源唯一）

| 消费方 | 装配方式 | 测试 |
| --- | --- | --- |
| **lieshou-boot**（开源单体·全栈） | 本地 Adapter | 10/10 |
| **auth-services**（微服务） | Feign Client | 13 全过 |
| **user-services**（微服务） | framework-common/domain/service | 46 全过 |
| **approval-services**（微服务） | Feign Client | 23 全过 |
| **lieshou-cloud-pro**（商业组合） | 间接同源（组合微服务仓） | — |

## 快速开始（作为依赖）

```bash
# 方式 A（推荐）：从内网 Nexus 解析，不需要本仓源码
# 前提：~/.m2/settings.xml 已 mirror 到 https://nexus.ts.huntercat.cn/repository/maven-public/
#       （新机器：ssh mesh-dell 'cat /srv/nexus/client-setup.sh' | bash）

# 方式 B：本地构建安装（改 framework 源码后联调）
mvn -B -ntp -DskipTests install

# 使用（consumer pom）：parent 指向 framework-parent（统一 parent/BOM）
# `framework-*` 依赖不写 <version>（由 framework-parent 的 dependencyManagement 提供）
<parent>
  <groupId>cn.huntercat</groupId>
  <artifactId>framework-parent</artifactId>
  <version>0.3.3</version>
  <!-- 方式 A：删掉 relativePath；方式 B（源码联调）：../../framework/framework-parent/pom.xml -->
</parent>

<dependency>
  <groupId>cn.huntercat</groupId>
  <artifactId>framework-auth</artifactId>
</dependency>
```

### 源码跳转（IDE 里看 framework 源码）

制品自带 `-sources.jar`（**0.3.2 起**），所以删掉 framework submodule 后仍能跳进源码：

```bash
mvn -B -ntp dependency:sources      # 或在 IDE 里打开「下载源码」开关
dependency:get -Dartifact=cn.huntercat:framework-common:0.3.3:jar:sources   # 单取某个模块
```

### 排障

- **报 `Missing: cn.huntercat:framework-*:jar:x.y.z` 但 Nexus 上明明有**：本地 `*.lastUpdated` 负缓存
  （Nexus 代理曾不可用的窗口会写下这些标记）→ `mvn -U ...`，或 `find ~/.m2/repository -name '*.lastUpdated' -delete`
- **报 PKIX / 证书不受信**：JVM 未信任内网 Caddy CA → 跑 `client-setup.sh`，或手工 `keytool -importcert`
- 其余（EULA 403 / 匿名读 / 负缓存 / 清理策略等）见 **`/srv/nexus/README.md` §8 排障**（单一出处，本仓不重复）

## 发布

- **v0.1.0**（RELEASE，2026-08）：https://gitlab.ts.huntercat.cn/huntercat-digital/lieshou-framework/-/tags/v0.1.0
- **v0.2.0**（RELEASE，2026-09-13）：https://gitlab.ts.huntercat.cn/huntercat-digital/lieshou-framework/-/tags/v0.2.0
- **v0.3.0**（RELEASE，2026-09-14）：https://gitlab.ts.huntercat.cn/huntercat-digital/lieshou-framework/-/tags/v0.3.0
- **v0.3.1**（RELEASE，2026-09-14）：首个入库内网 Nexus 的版本 —— https://gitlab.ts.huntercat.cn/huntercat-digital/lieshou-framework/-/tags/v0.3.1
- **v0.3.2**（RELEASE，2026-09-14）：随制品发布 **sources jar**（9 个 `framework-*`）—— 删掉 framework submodule 后 IDE 仍可跳进源码（`mvn dependency:sources`）—— https://gitlab.ts.huntercat.cn/huntercat-digital/lieshou-framework/-/tags/v0.3.2
- **v0.3.3**（RELEASE，2026-09-15）：把 E48 两条修复（SSE/异步断连 500 + 4xx 被吞成 500）从侧分支合入 main 并**首次随制品发布** —— 按坐标从 Nexus 解析的消费方须用 ≥ 0.3.3 —— https://gitlab.ts.huntercat.cn/huntercat-digital/lieshou-framework/-/tags/v0.3.3
- 版本策略：SemVer；业务改动 → 本仓发布新版本 → 消费方 bump

### 发布流程（自动化）

改 `<version>`（根 pom + 子模块 parent + framework-parent 的 `lieshou-framework.version`，共 12 处）
→ 合入 main（CI 跑 `verify` 门）→ 打 tag `vX.Y.Z`：

```bash
# tag 必须与 pom 版本一致（CI 会校验，不一致直接拒发）
git tag -a v0.3.3 -m "0.3.3：..." && git push origin v0.3.3
```

tag pipeline 的 `deploy` stage 执行 `mvn deploy` → `maven-releases`
（`https://nexus.ts.huntercat.cn/repository/maven-releases/cn/huntercat/...`）；
凭证在项目 CI 变量 `NEXUS_DEPLOY_USER` / `NEXUS_DEPLOY_PASS`，不落仓库。

> ⚠️ `deploy` **只认语义化 tag** `vX.Y.Z`。里程碑锚点 tag（`release-*`、`<主题>-YYYY-MM`）只跑 `verify`，
> 不触发发布 —— 否则会被 tag↔pom 版本校验判为不一致而把整条 pipeline 变红。
>
> 另：`maven-releases` **禁止重发**（同一坐标永不重新发布）。漏发内容只能发下一个 patch 版本，
> 例：0.3.1 漏了 sources → 0.3.2 补上，0.3.1 已发布的字节一个不改。

## 产品线关系

```
lieshou-framework  ←—— 唯一业务逻辑源（开源 · Apache-2.0）
      ↑
┌─────┴─────────────┐
lieshou-boot（单体）  lieshou-cloud（微服务）
└─ 开源 · Apache-2.0 ┘        lieshou-cloud-pro（商业）组合微服务仓
```

## License

Apache-2.0。详见 [LICENSE](./LICENSE)。
