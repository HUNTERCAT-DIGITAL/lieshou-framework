# ADR-0044 · 两端 Controller 内联业务上收 framework-service

- 状态: **摘要（原文待补）**
- 日期: 2026-08（据 `AGENTS.md`）
- 影响面: framework-service / lieshou-boot / 全部底座服务

## 已知摘要（来自仓内引用，非原文）

单体（lieshou-boot）与微服务两端 Controller 里的内联业务**上收**到 `framework-service`，
各端只留 Controller + 装配 + 启动。已上收：角色 / 邀请码 / 租户 / 用户生命周期 / 通知 / 验证码 / 审计。
2026-09 已入库的阶段 1-3 见 `CHANGELOG.md` 的 0.2.0 段。

## 原文出处

- 待补（`AGENTS.md` 当前阶段 / `CHANGELOG.md` 引用编号）
