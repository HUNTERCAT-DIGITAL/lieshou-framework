# LieShou-framework · 猎手云核心框架

> 猎手云产品线的**核心能力框架**（上游同源唯一）：认证 / JWT / 统一异常 / 审计 / 权限。
> **LieShouBoot（单体）与 LieShouCloud（微服务）共用的薄壳底座**——业务逻辑只在此维护，两端装配引用。

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring_Boot-3.5-green" alt="Spring Boot 3.5"/>
  <img src="https://img.shields.io/badge/License-Apache--2.0-brightgreen" alt="Apache-2.0"/>
</p>

## 设计哲学（借鉴 Spring 分层）

```
Spring Framework  →  LieShou-framework  核心能力层（业务逻辑唯一源）
Spring Boot       →  LieShouBoot       单体快速启动（Framework + 装配）
Spring Cloud      →  LieShouCloud      微服务/分布式（Framework + 服务发现/网关）
```

**规则**：业务逻辑（Service / 端口 / DTO）只进 Framework；各端只留 Controller + 装配 + 启动。

## 模块

| 模块 | 内容 | 依赖 |
| --- | --- | --- |
| **framework-jwt** | JWT 签发/解析/密钥（JwtSupport）+ 自动装配 | 无 Web 依赖（servlet/webflux 通用） |
| **framework-common** | 统一异常 / ErrorCode / 租户上下文 / 审计注解 / 权限注解 | servlet 栈服务共用 |
| **framework-auth** | 认证业务：AuthService / JwtService / UserAuthPort（端口）/ DTO | framework-jwt + spring-security |

## 端口-适配器模式

```java
// framework-auth 定义端口（业务只依赖端口）
public interface UserAuthPort {
  UserAuthView findByTenantAndUsername(String tenantCode, String username);
  ...
}

// LieShouBoot（单体）实现：本地调用 user 模块
public class UserAuthAdapter implements UserAuthPort { ... }

// LieShouCloud（微服务）实现：Feign 调用 user-service
public interface UserAuthClient extends UserAuthPort { ... }
```

## 快速开始（作为依赖）

```bash
# 构建安装到本地 maven 仓库
mvn -B -ntp -DskipTests install

# 使用（consumer pom）
<dependency>
  <groupId>cn.huntercat</groupId>
  <artifactId>framework-auth</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 产品线关系

```
LieShou-framework  ←—— 唯一业务逻辑源
      ↑
┌─────┴─────────────┐
LieShouBoot（单体）  LieShouCloud（微服务）
└─ 开源 · Apache-2.0 ┘        LieShouCloudPro（商业）组合微服务仓
```

## License

Apache-2.0。详见 [LICENSE](./LICENSE)。
