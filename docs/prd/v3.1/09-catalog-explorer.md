# 阶段 9：Catalog Explorer 前端目录体验 PRD

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 产品 | RayFlow v3.1 自治数据治理与 AI-Ready 平台 |
| 模块 | Catalog Explorer / 数据目录 |
| 文档类型 | 阶段产品需求文档 |
| 目标读者 | 产品、设计、前端、后端、数据治理和架构团队 |
| 状态 | Draft |
| 日期 | 2026-08-28 |

## 2. 设计依据与产品判断

本模块参考 Databricks Catalog Explorer 的信息架构：一类入口用于按业务域和推荐结果发现高价值资产，另一类入口用于按完整 Catalog → Schema → Object 层级检查对象细节、权限、样例数据和血缘。RayFlow 在此基础上增加治理 Issue、AI-Ready 评分和平台适配器状态。

RayFlow 不复制 Databricks 的产品名称或底层对象，而是将同样的信息架构映射到 Flink、Paimon、Fluss、Doris 和 AI Asset。当前仓库中的 StarRocks 目录代码视为 Doris 适配的实现基础，用户界面和产品契约不出现 StarRocks 作为主产品名称：

```text
Discover
  面向业务用户：推荐资产、业务域、AI-Ready、认证、热门度

Catalog Explorer
  面向数据用户：Catalog → Schema → Table/View/Topic/Job/AI Asset
  查看 Schema、样例、质量、血缘、使用、策略、权限和操作
```

## 3. 产品目标

- 让用户先找到“值得使用的资产”，再深入了解资产细节。
- 将现有资源中心的 Paimon、Fluss、StarRocks 平铺连接管理升级为统一资产目录体验。
- 在同一个详情页呈现 Schema、语义、质量、血缘、使用、Owner、策略和 AI-Ready 状态。
- 让用户在不运行 Flink 作业的情况下完成元数据浏览、搜索、权限请求和治理判断；只有预览或执行查询时才依赖对应计算资源。
- 通过认证、弃用、热门度、质量分数和 AI-Ready 分数建立可解释的信任层。

## 4. 非目标

- 不把 Catalog Explorer 做成通用 SQL IDE；查询编辑器只提供有限的“在开发页打开”入口。
- 不在前端直接连接 Paimon、Doris 或 Fluss；所有元数据和数据访问由后端 API 控制。
- 不用一个统一表格强行隐藏不同平台的能力差异；平台差异通过对象类型和能力标签表达。
- 不把 AI 推荐结果当作事实；所有推荐必须展示来源、更新时间和解释。

## 5. 用户角色

| 角色 | 主要任务 | 默认入口 |
|---|---|---|
| 数据消费者 | 搜索可信数据、查看定义、预览样例、申请权限 | Discover |
| 数据工程师 | 检查 Schema、血缘、质量、使用和变更影响 | Catalog Explorer |
| 治理管理员 | 管理 Owner、标签、策略、认证、弃用和权限 | Catalog Explorer |
| AI/分析工程师 | 查找 AI-Ready 资产、读取 Context、查看可用字段 | Discover + AI-Ready |
| 平台管理员 | 管理连接、采集、同步状态和跨租户资源 | Resource Center + Catalog Explorer |

## 6. 信息架构

### 6.1 一级导航

```text
数据目录
├── Discover
├── Catalog Explorer
├── AI-Ready Assets
├── 我的收藏
└── 最近访问
```

现有“资源中心”保留连接配置和运行资源管理职责，但不再承担主要的数据发现职责：

```text
资源中心：连接、凭证、Flink Runtime、JAR、采集配置
数据目录：资产发现、理解、治理、预览和 Context
```

### 6.2 Discover 页面

面向“我应该使用什么数据”的任务，内容从业务域、资产类型和推荐资产开始，而不是从技术连接开始。

页面区域：

1. 全局搜索框：支持资产名、列名、评论、Glossary、标签和语义搜索。
2. 推荐区：认证资产、AI-Ready 资产、热门资产、近期更新和治理风险较低资产。
3. 业务域区：按业务域/子域浏览资产，支持治理管理员配置精选区块。
4. 资产类型区：表、视图、Topic、Flink Job、模型、AI Asset。
5. 快速筛选：平台、租户、Owner、认证状态、质量等级、AI-Ready 等级、更新时间。

### 6.3 Catalog Explorer 页面

面向“我需要检查完整对象”的任务，采用左树右列表布局：

```text
┌──────────────────────────────────────────────────────┐
│ 搜索资产                         新建 / 采集 / 筛选 │
├──────────────┬───────────────────────────────────────┤
│ Catalog Tree  │ 当前层级、面包屑、对象列表、排序     │
│              │ 名称 | 类型 | Owner | 质量 | 更新 | 状态│
│ catalog      │                                       │
│  └ schema    │                                       │
│     ├ table  │                                       │
│     └ view   │                                       │
└──────────────┴───────────────────────────────────────┘
```

左侧树支持统一的 Catalog → Schema → Object 层级；对 Fluss 使用 Cluster → Topic 映射，对 Paimon 使用 Catalog → Database → Table 映射，对 Doris 使用 Connection → Database → Object 映射。对象列表支持分页、虚拟滚动、列配置、筛选和批量收藏。列表内名称、类型、Owner、认证、质量、AI-Ready 和最近更新时间必须可直接判断。

## 7. 搜索与发现

### CE-001 统一搜索

搜索范围包括资产名称、列名、对象评论、列评论、Glossary、Owner、标签、平台和 AI Context。结果只返回当前用户有权发现的资产；元数据可发现不等于可读取数据。

### CE-002 搜索模式

- 关键词搜索：匹配名称、注释、标签和 Glossary。
- 语义搜索：目标能力，匹配业务含义和列语义，并展示匹配原因；在语义索引未就绪前不得伪装成已支持。
- 精确路径搜索：支持 `catalog.schema.object` 和 `assetKey`。

### CE-003 结果卡片

每个结果必须展示：

- 对象名称、完整路径和对象类型。
- 平台徽标：Paimon、Doris、Fluss、Flink、AI Asset。
- 一句话描述和关键列摘要。
- 认证/弃用状态。
- Owner 和最后更新时间。
- 质量等级、AI-Ready 等级和热门度。
- 权限状态：可预览、需申请权限、仅可查看元数据。

### CE-004 筛选器

筛选器必须支持组合条件并可保存为个人视图：平台、对象类型、业务域、Owner、标签、认证状态、质量等级、AI-Ready 状态、更新时间、数据新鲜度和治理风险。

## 8. 资产详情页

详情页采用 Databricks 风格的“Overview + 分页详情”模式，但针对 RayFlow 增加治理与 AI-Ready 信息：

```text
Overview | Schema | Sample Data | Lineage | Quality | Usage
Governance | AI-Ready | Permissions | Activity
```

### 8.1 页面头部

必须展示：

- 对象完整路径、类型、平台和连接来源。
- 认证、弃用、AI-Ready、质量等级和新鲜度徽章。
- Owner、业务域、最后采集时间、Schema 版本。
- 收藏、复制路径、请求权限、打开开发页、更多操作。
- 描述和 AI 生成摘要；AI 生成内容必须明确标记并允许人工编辑。

### 8.2 Overview

展示对象定义、业务用途、关键指标、Owner、标签、Glossary、访问条件、数据新鲜度、认证依据和治理摘要。

当信息缺失时显示可执行的补齐入口，例如“未设置 Owner”“缺少业务定义”“质量尚未评估”，而不是留白。

### 8.3 Schema

表格字段：列名、类型、是否可空、主键/分区、注释、敏感分类、质量摘要、热门度和关联 Glossary。支持列级搜索、复制字段名、查看历史变更和按敏感级别筛选。

### 8.4 Sample Data

- 默认展示经过权限校验和脱敏的有限样例。
- 清楚标注样例时间、来源、行数限制和是否实时。
- 无 SELECT 权限时仍可查看允许的元数据，并展示“申请访问”入口。
- Paimon/Doris 可通过后端代理预览；Fluss 默认展示 Topic Schema、分区和消息摘要，不直接暴露无限制消费。

### 8.5 Lineage

提供表级和列级血缘图，支持上下游展开、时间范围、作业过滤和影响分析。节点必须展示对象类型、平台、状态和权限；血缘边必须展示来源、更新时间和可信度。

### 8.6 Quality

展示完整度、准确性、唯一性、及时性、有效性、空值率、重复率、延迟和最近检测结果。每个指标显示当前值、阈值、趋势、检测时间和证据来源。

### 8.7 Usage

展示近 30 天访问趋势、热门列、常用用户/团队、关联作业、关联工作流、常用下游资产和最近访问。没有使用数据时显示“尚无使用记录”和数据来源说明。

### 8.8 Governance

集中展示策略命中、Issue、分类、Owner、认证、弃用、保留期限、访问权限和审计活动。用户可以从治理问题直接进入对应 Issue、审批或整改 Skill。

### 8.9 AI-Ready

展示总分、等级、八个维度得分、硬门槛、阻断项、证据和整改建议。提供“查看 Context”和“发布/撤回”操作，但操作必须遵循权限、审批和 Verification 状态。

### 8.10 Permissions

展示用户当前拥有的发现、预览、查询、修改和管理权限；支持申请权限并显示审批人/目标系统。不得在前端展示用户无权查看的敏感授权细节。

## 9. 信任信号设计

信任信号必须同时出现在搜索结果、列表、详情头部和 AI Context：

| 信号 | 含义 | 展示 |
|---|---|---|
| Certified | 经过组织审核 | 蓝色认证徽章 + 认证人/时间 |
| Deprecated | 不建议继续使用 | 琥珀色警告 + 替代资产 |
| Quality | 最近质量检测结果 | 等级徽章 + 分数 + 趋势 |
| Freshness | 数据更新是否符合预期 | 新鲜/延迟/过期状态 |
| Popularity | 近期使用热度 | 热门度/访问趋势 |
| AI-Ready | 是否满足 AI Context Contract | 分数、等级和阻断项 |
| Governance Risk | 未处理风险 | 风险等级和 Issue 数量 |

颜色只表达语义状态，不作为唯一信息；所有徽章必须有文字、Tooltip 或详情解释。禁止用红色表示普通业务状态，红色仅用于阻断、失败或严重风险。

## 10. 采集与新鲜度体验

- Catalog 顶部显示最近一次成功采集时间、正在运行的采集任务和失败数量。
- 用户可对 Catalog、Schema 或单个资产发起刷新，长耗时操作返回任务状态。
- 采集失败时保留最近有效快照，同时在页面显示“数据可能过期”和失败原因。
- 空目录第一次加载时提供刷新和连接检查入口，不把暂时空结果误认为没有资产。
- 详情页每个区块分别显示数据更新时间，避免一个时间戳掩盖不同来源的新鲜度。

## 11. 权限与多租户

- 所有搜索、树浏览、详情、预览、血缘、导出和权限请求都按 `tenantId` 隔离。
- 支持“可发现但不可读取”：用户可以发现资产名称、Owner、描述和申请入口，但不能看到未授权样例或敏感字段值。
- 资产 Owner、治理管理员和平台管理员的操作按钮按权限动态展示。
- 申请权限必须记录申请人、资产、权限类型、理由、审批人、状态和处理时间。
- 跨租户平台管理员访问必须有明确租户切换上下文并产生审计事件。

## 12. 交互状态要求

每个页面和详情 Tab 必须具备以下状态：

- Loading：骨架屏保持布局稳定。
- Empty：说明没有数据、没有权限还是尚未采集，并提供下一步操作。
- Error：展示可理解的原因、Trace ID、重试和查看依赖状态入口。
- Stale：明确标注数据过期时间和最近有效版本。
- Partial：区分已加载区块和不可用区块，不阻断整页浏览。
- Permission denied：说明当前可查看范围，并提供申请权限动作。

## 13. API 契约

```http
GET  /api/catalog/discover
GET  /api/catalog/search?q=&mode=keyword|semantic
GET  /api/catalog/tree
GET  /api/catalog/objects
GET  /api/catalog/objects/{assetKey}
GET  /api/catalog/objects/{assetKey}/context
GET  /api/catalog/objects/{assetKey}/schema
GET  /api/catalog/objects/{assetKey}/sample
GET  /api/catalog/objects/{assetKey}/lineage
GET  /api/catalog/objects/{assetKey}/quality
GET  /api/catalog/objects/{assetKey}/usage
GET  /api/catalog/objects/{assetKey}/permissions
POST /api/catalog/objects/{assetKey}:refresh
POST /api/catalog/objects/{assetKey}:request-access
POST /api/catalog/views
```

这些是目标统一目录 API，不替换当前已有的 `/api/paimon`、`/api/starrocks`、`/api/fluss` 和 `/api/flink` 接口。首期通过后端 Facade/Adapter 复用现有接口，待统一 Metadata 数据模型稳定后再逐步迁移调用方。统一响应必须包含 `tenantId`、`assetKey`、`observedAt`、`source`、`version` 和 `freshness`。列表接口支持 `page`、`pageSize`、`sort`、`filters` 和 `cursor`；详情区块失败不得导致其他区块响应失败。

## 14. 核心数据模型

```json
{
  "assetKey": "tenant-1:paimon:catalog.db.customer",
  "displayName": "customer",
  "qualifiedName": "catalog.db.customer",
  "assetType": "TABLE",
  "platform": "PAIMON",
  "businessDomain": "customer",
  "owner": { "id": "team-data", "name": "数据平台组" },
  "trust": {
    "certified": true,
    "deprecated": false,
    "qualityScore": 92,
    "freshness": "FRESH",
    "popularity": 87,
    "governanceRisk": "LOW"
  },
  "aiReadiness": {
    "level": "AI_READY",
    "score": 95,
    "blockingDimensions": []
  },
  "observedAt": "2026-08-28T00:00:00Z",
  "source": "PAIMON_COLLECTOR",
  "metadataVersion": 7
}
```

## 15. 关键操作

### 15.1 收藏与最近访问

收藏和最近访问按用户和租户保存。最近访问只记录资产标识和时间，不记录未授权数据内容。

### 15.2 复制路径

一键复制完整路径、Asset Key、API Context 地址和可用于 Flink SQL 的引用。复制操作不泄露访问凭证。

### 15.3 打开开发页

从表、Topic 或作业详情进入开发页时携带资产上下文、租户、连接和只读引用；不得将密钥或原始密码传入 URL。

### 15.4 申请权限

用户选择权限类型并填写理由，系统根据 Owner、域管理员或平台配置路由审批。申请状态在资产详情和通知中心同步展示。

### 15.5 高危操作

删除、清空、Snapshot 清理、Compaction、Topic 修改和迁移切换必须从详情页进入操作确认流程，展示影响范围、风险、审批要求、预检查和回滚信息。

## 16. 前端验收标准

- 用户可以从 Discover 通过关键词找到 Paimon、Doris、Fluss 和 AI Asset；语义搜索在索引能力上线后启用。
- 用户可以从 Catalog Explorer 逐级浏览 Catalog、Schema 和对象，不需要先进入某个连接 Tab。
- 资产列表能同时展示平台、类型、Owner、认证、质量、AI-Ready 和新鲜度。
- 资产详情至少具备 Overview、Schema、Sample Data、Lineage、Quality、Usage、Governance、AI-Ready 和 Permissions。
- 没有数据、没有权限、采集过期、部分失败和服务错误均有清晰状态和下一步动作。
- 未授权用户不能通过搜索、详情、样例、缓存或前端路由读取其他租户或敏感内容。
- AI-Ready 评分可以追溯到维度和证据，认证/弃用可以追溯到操作人和时间。
- Paimon、Doris、Fluss 的平台差异在 UI 中可理解，且不破坏统一目录体验；Doris 首期可复用现有 StarRocks 实现，但用户界面和产品命名统一显示 Doris。
- 从资产详情发起刷新、权限申请、开发页跳转和高危操作时，状态、审批和审计记录完整。

## 17. 产品指标

- 搜索成功率：搜索后打开有效资产详情的比例。
- 首次找到资产耗时：从进入目录到打开目标资产详情的时间。
- 可信资产使用率：认证或 AI-Ready 资产在全部访问中的占比。
- 元数据完整率：Owner、描述、Schema、血缘、质量和分类的覆盖率。
- Context 消费量：AI Context API 调用次数和成功率。
- 权限申请转化率：发现资产后发起申请并最终获得权限的比例。
- 过期资产发现率：用户访问前被新鲜度信号拦截或提示的比例。
- 治理入口转化率：从资产问题进入 Issue、整改 Skill 或 AI-Ready Assessment 的比例。

## 18. 设计落地原则

- 优先采用“业务发现 + 技术浏览”双模式，而不是在一个页面混合所有用户任务。
- 默认先展示信任和用途，再展示底层技术细节；详情页保持渐进式披露。
- 所有 AI 生成内容必须与事实元数据区分，并展示生成时间和来源。
- 所有信任分数必须可解释、可追溯、可按权限过滤，不能只展示一个彩色分数。
- 目录必须支持键盘访问、清晰焦点态、表格列配置和响应式窄屏降级。
- 统一使用现有 RayFlow 设计系统和 API SDK，不在每个平台面板内重复实现搜索、权限和状态组件。

## 19. 与现有 RayFlow 的映射

| 现有能力 | Catalog Explorer 复用/改造 |
|---|---|
| Paimon Connections Panel | 保留连接管理；将浏览能力接入统一 Catalog |
| Fluss Connections Panel | 保留集群管理；Topic 映射为 Catalog Object |
| 现有 StarRocks Connections Panel | 作为 Doris 适配基础保留或重命名；库表映射为 Catalog/Schema/Object，用户界面统一显示 Doris |
| Resource Center | 作为平台资源配置入口，不再作为主要数据发现入口 |
| Paimon 浏览 API | 抽象为统一 Catalog API，保留平台扩展字段 |
| OpenAPI 生成 SDK | 继续作为前端接口唯一类型来源 |
| 租户上下文 | 贯穿搜索、缓存、详情、预览和权限请求 |

## 20. 参考原则

本 PRD 参考 Databricks 官方对 Catalog Explorer、Discover、搜索、标签、血缘、认证/弃用、权限发现和使用洞察的产品思路；具体实现仍以 RayFlow 的 Flink/Paimon/Fluss/Doris 能力、租户模型和治理闭环为准。
