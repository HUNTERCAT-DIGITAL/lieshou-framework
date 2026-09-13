# ADR-0002 · 破坏性变更必须在 CHANGELOG 显式声明

- 状态: **摘要（原文待补）**
- 日期: 未知（早于 2026-08）
- 影响面: framework 全部公共 API + 全部消费方（lieshou-boot / 5 个底座服务 / 行业包）

## 已知摘要（来自仓内引用，非原文）

framework 是 5+ 底座服务与 lieshou-boot 共用的**上游同源唯一**。公共 API 变更（包移动 / 类删除 / 签名变化）
必须在 `CHANGELOG.md` 记录，消费方升级前对照评估适配范围。`CHANGELOG.md` 头部引用本编号。

## 原文出处

- 待补（`CHANGELOG.md` 仅引用编号，无正文）
