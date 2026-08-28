# RayFlow v3.1 阶段 PRD 文档集

## 文档定位

本目录将 RayFlow v3.1 的完整产品方案拆成相互衔接的阶段 PRD。每份 PRD 都可以独立评审，同时通过统一的资产、事件、任务、权限和审计契约形成完整闭环。

产品闭环：

```text
SENSE → UNDERSTAND → DECIDE → ACT → VERIFY → PUBLISH → LEARN
```

## 阶段目录

| 阶段 | 文档 | 核心结果 | 依赖 |
|---|---|---|---|
| 1 | [Metadata Plane](01-metadata-plane.md) | 可查询、可追踪的统一 Asset Context | 现有资源中心、PostgreSQL、采集器 |
| 2 | [Governance Detection](02-governance-detection.md) | 事件驱动的策略、质量和问题发现 | Metadata Plane、Redis Streams |
| 3 | [Migration Engine](03-migration-engine.md) | Legacy 到 Fluss/Flink/Paimon/Doris 的迁移闭环 | Metadata Plane、Flink、目标平台 |
| 4 | [AI Decision](04-ai-decision.md) | 基于证据的结构化 AI 决策 | Governance Detection、统一模型网关 |
| 5 | [Skill and Capability](05-skill-capability-runtime.md) | 可审批、可编排、可执行的治理动作 | AI Decision、RayFlow DAG |
| 6 | [Verification](06-verification-engine.md) | 对治理结果进行可证明的验证 | Capability Runtime、Metadata Plane |
| 7 | [AI-Ready Publish](07-ai-ready-publish.md) | 将合格资产发布为 AI 可消费资产 | Verification、AI Context Contract |
| 8 | [MLflow and Learning](08-mlflow-learning.md) | 追踪、评估、预测和反馈学习 | 全部前置阶段、MLflow |
| 9 | [Catalog Explorer](09-catalog-explorer.md) | 统一发现、理解和治理数据与 AI 资产 | Metadata Plane、治理状态、前端控制台 |

## 共享产品原则

- 所有资产、事件、执行任务、Skill、Capability 和 AI Context 都必须携带 `tenantId`。
- AI 负责理解、推理和建议，不直接调用底层数据平台操作。
- 所有实际动作必须经过 Skill、Capability 权限检查和分级审批。
- 所有自动治理动作必须具备幂等键、超时、重试策略和 Verification Contract。
- 所有事件至少一次投递；消费者必须幂等，失败事件进入死信队列。
- 所有敏感配置在存储和日志中脱敏；模型输入遵循最小必要数据原则。
- 所有公共接口统一使用异步任务模型处理长耗时采集、迁移、执行和发布。

## 统一对象

```text
Tenant
Asset
AssetContext
GovernanceEvent
GovernanceIssue
Policy
Decision
Skill
Capability
ExecutionRun
VerificationRun
AIReadyAssessment
AIAsset
GovernanceRun
```

## 统一状态

- 任务状态：`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELED`、`RETRYING`。
- 风险等级：`LOW`、`MEDIUM`、`HIGH`、`CRITICAL`。
- 资产 AI-Ready 状态：`NOT_ASSESSED`、`NOT_READY`、`CONDITIONALLY_READY`、`AI_READY`、`PUBLISHED`。
- 审批状态：`NOT_REQUIRED`、`PENDING`、`APPROVED`、`REJECTED`、`EXPIRED`。

## 评审方式

Catalog Explorer 是横向产品入口，依赖 Metadata Plane，但不必等待 MLflow 或预测治理完成；可以先以现有 Paimon、Doris、Fluss 浏览能力提供基础目录，再逐步接入质量、血缘、Usage 和 AI-Ready。本文档集不包含具体开发排期、人员分工或代码拆分。

## 当前仓库基线

| 能力 | 当前状态 | PRD 中的处理 |
|---|---|---|
| Paimon | 已有连接、Database/Table、Schema、定义、文件、Snapshot 和预览浏览 | 作为首个完整 Catalog 数据源 |
| Doris | 当前仓库已有一套名为 StarRocks 的连接、库表、Schema、分区、预览、DDL 和查询实现 | 产品层统一按 Doris 定义；现有 StarRocks 实现作为可复用适配基础，需确认协议兼容并完成命名/配置适配 |
| Fluss | 已有集群和 Topic 管理基础 | 首期展示 Topic/Schema，消费分析后置 |
| Flink | 已有 Runtime、Job、SQL/JAR、调度和执行能力 | 作为 Job/运行来源和血缘来源 |
| PostgreSQL/Flyway | 已有业务实体和迁移机制 | 新增目录域表时沿用现有迁移规范 |
| Redis Streams | 当前代码未形成 Governance Event Bus | 作为目标新增基础设施，不假设已可复用 |
| OpenLineage | 当前代码未发现接入 | 作为目标采集适配器，先定义契约 |
| MLflow | 当前代码未发现接入 | 作为后续目标集成，不作为目录基础依赖 |
