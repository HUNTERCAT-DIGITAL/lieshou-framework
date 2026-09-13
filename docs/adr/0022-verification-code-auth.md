# ADR-0022 · 多租户下的验证码登录与手机号唯一

- 状态: **摘要（原文待补）**
- 日期: 2026-08（据 `AGENTS.md`）
- 影响面: framework-auth / framework-service / user-services / lieshou-boot

## 已知摘要（来自仓内引用，非原文）

验证码登录与注册落地；JWT 携带租户信息（`tid` / `tcode`）；多租户隔离下手机号在**租户内唯一**
（`UserRepository.findByTenantIdAndPhone`），密码登录支持"账号或手机号"。

## 原文出处

- 待补（`AGENTS.md` 关键决策 2026-08 引用编号）
