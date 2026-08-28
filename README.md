# lieshou-framework · 猎手云核心框架

> 猎手云产品线的**核心能力框架**（上游同源唯一）：认证 / JWT / 统一异常 / 审计 / 权限 / 领域模型 / 审批。
> **lieshou-boot（单体）与 lieshou-cloud（微服务）共用的薄壳底座**——业务逻辑只在此维护，两端装配引用。
> **v0.1.0（RELEASE）** · 六模块 · 106 测试全过

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-green" alt="Spring Boot 3.5"/>
  <img src="https://img.shields.io/badge/Version-0.1.0-blue" alt="v0.1.0"/>
  <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen" alt="Apache-2.0"/>
</p>

## 设计哲学（借鉴 Spring 分层）

```
Spring Framework  →  lieshou-framework  核心能力层（业务逻辑唯一源）
Spring Boot       →  lieshou-boot       单体快速启动（Framework + 装配）
Spring Cloud      →  lieshou-cloud      微服务/分布式（Framework + 服务发现/网关）
```

**规则**：业务逻辑（Service / 端口 / DTO / 领域模型）只进 Framework；各端只留 Controller + 装配 + 启动。

## 模块（六模块 · v0.1.0）

| 模块 | 内容 | 依赖 |
| --- | --- | --- |
| **framework-jwt** | JWT 签发/解析/密钥（JwtSupport）+ 自动装配 | 无 Web 依赖（servlet/webflux 通用） |
| **framework-common** | 统一异常 / ErrorCode / 租户上下文 / 审计注解 / 权限注解 | servlet 栈服务共用 |
| **framework-domain** | 领域模型（JPA 实体 + Repository）：用户/租户/角色/验证码/审计/通知 | spring-data-jpa |
| **framework-service** | 领域服务：用户/租户/角色/邀请码/通知/验证码/审计生命周期（ADR-0044 上收）+ CodeSender 端口 | domain + common |
| **framework-approval** | 审批业务：ApprovalService 状态机 + 审批实体 + UserQueryPort/NotifierPort | domain + common |
| **framework-auth** | 认证业务：AuthService（登录/验证码/注册/重置/切换租户/多租户选项）/ JwtService / UserAuthPort（端口）/ DTO | jwt + security |

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
# 构建安装到本地 maven 仓库
mvn -B -ntp -DskipTests install

# 使用（consumer pom）
<dependency>
  <groupId>cn.huntercat</groupId>
  <artifactId>framework-auth</artifactId>
  <version>0.1.0</version>
</dependency>
```

## 发布

- **v0.1.0**（RELEASE，2026-09）：https://github.com/HUNTERCAT-DIGITAL/lieshou-framework/releases/tag/v0.1.0
- 版本策略：SemVer；业务改动 → 本仓发布新版本 → 消费方 bump

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
