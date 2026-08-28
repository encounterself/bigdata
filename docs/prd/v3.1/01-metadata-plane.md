# 阶段 1：Metadata Plane PRD

## 1. 目标

建立统一的 Metadata Plane，将 Doris、Paimon、Fluss、Flink、OpenLineage、Usage 和 Migration 的信息归一为可检索、可关联、可审计的 `AssetContext`，为治理检测、AI 决策和 AI-Ready 评估提供事实基础。

## 2. 用户与场景

- 数据管理员：查看资产、负责人、分类、质量和血缘。
- 研发工程师：了解 Schema、依赖、使用方和变更影响。
- 治理人员：根据质量、策略和生命周期信息处理问题。
- AI 消费者：通过 Context API 获取可解释的资产上下文。

## 3. 范围

### 包含

- 统一资产、列、统计、质量、血缘、使用、生命周期、Owner、Glossary 和 AI Asset 模型。
- Doris、Paimon、Fluss、Flink 五类首期适配器；OpenLineage、Usage 和 Migration 作为扩展适配器。当前仓库中已有的 StarRocks 连接实现作为 Doris 适配的复用基础，但产品对象、命名和配置必须以 Doris 为准。
- 周期采集、事件采集、手动刷新和幂等 Upsert。
- Asset Context 聚合、版本、数据新鲜度和来源追踪。

### 不包含

- 本阶段不执行治理动作。
- 本阶段不让 Collector 直接写业务治理表。
- 本阶段不决定具体物理数据库表名，但必须支持按领域迁移表结构。

## 4. 功能需求

### MD-001 资产注册

系统必须支持以稳定的 `assetKey` 标识资产，至少包含 `tenantId`、平台类型、连接标识、命名空间、名称、类型、状态、Owner 和最后采集时间。重复采集不得产生重复资产。

### MD-002 Schema 与统计

系统必须保存列名、类型、是否可空、默认值、主键/分区信息、敏感分类、注释、样本统计、空值率、唯一率、基数和数据新鲜度。

### MD-003 血缘

系统必须接收 OpenLineage 事件并转换成 Canonical Lineage Event，再由 Metadata Service 写入统一血缘模型。血缘记录必须保留来源、事件时间、作业标识和解析状态。

### MD-004 采集任务

支持全量采集、增量采集、按资产采集和失败重试。采集任务须可查询状态、耗时、行数、错误摘要和数据版本。

### MD-005 Context 聚合

系统必须提供资产详情和聚合上下文，返回资产、Schema、统计、质量、Owner、分类、血缘、使用、策略和 AI-Ready 摘要。每个区块必须标记 `observedAt`、`source` 和 `confidence`。

### MD-006 变更识别

Schema、Owner、Classification、Lineage、Quality 和 Lifecycle 发生有效变化时，产生版本记录及对应 Governance Event。

### MD-007 租户隔离

任何查询、采集、事件、缓存和导出都必须校验 `tenantId`。平台管理员跨租户访问必须明确记录审计原因。

## 5. 关键数据契约

```json
{
  "assetKey": "tenant-1:paimon:catalog.db.table",
  "tenantId": 1,
  "platform": "PAIMON",
  "namespace": "catalog.db",
  "name": "table",
  "assetType": "TABLE",
  "schemaVersion": 7,
  "observedAt": "2026-08-28T00:00:00Z",
  "source": "PAIMON_COLLECTOR",
  "freshness": "FRESH",
  "columns": [],
  "quality": {},
  "lineage": {},
  "ownership": {},
  "classification": {},
  "aiReadiness": {}
}
```

## 6. 接口

```http
GET  /api/assets
GET  /api/assets/{assetKey}
GET  /api/assets/{assetKey}/context
GET  /api/assets/{assetKey}/schema
GET  /api/assets/{assetKey}/lineage
GET  /api/assets/{assetKey}/quality
POST /api/metadata/collect
GET  /api/metadata/collect-runs/{runId}
```

长耗时采集接口返回 `202 Accepted` 和 `runId`。Context API 返回 `404` 表示资产不存在，返回 `409` 表示上下文版本冲突，返回 `503` 表示依赖源不可用但可返回最近一次快照。

## 7. 验收标准

- 同一租户、同一资产重复采集后只有一个逻辑资产。
- 可查询完整 Asset Context，并展示每个字段的来源和更新时间。
- 首期完成 Paimon、Doris、Fluss 的最小资产采集；Paimon 完成完整 Schema/文件/Snapshot，Doris 完成库表/Schema，Fluss 完成集群/Topic/Schema。
- OpenLineage 事件可以被转换、去重并关联到上下游资产。
- 租户 A 无法通过 ID、名称、事件或缓存读取租户 B 的资产。
- 采集失败可重试，部分源不可用时不覆盖已有有效快照。
