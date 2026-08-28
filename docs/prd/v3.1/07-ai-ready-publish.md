# 阶段 7：AI-Ready Publish PRD

## 1. 目标

将完成治理且满足 AI Context Contract 的数据资产评估为 AI-Ready，并通过 Publisher 注册可被 AI Agent 直接消费的 AI Asset 和 Context API。

## 2. AI-Ready 维度

- Schema：字段类型、主键、分区和版本稳定。
- Semantic：字段含义、Glossary 和业务定义完整。
- Quality：质量指标达到策略阈值。
- Lineage：上下游血缘可追溯。
- Ownership：Owner、联系人和责任边界明确。
- Classification：敏感等级和使用限制已标注。
- Policy：适用策略已评估且无未处理违规。
- Freshness：更新频率和延迟满足消费者要求。

## 3. 判定规则

采用“硬门槛 + 加权评分”：

- Schema、Quality、Lineage、Ownership、Classification、Policy 为关键维度。
- 任一关键维度未达到最低门槛时不得进入 `AI_READY`。
- 综合分数用于排序和解释，默认等级为：
  - `AI_READY`：分数 `>= 90` 且所有硬门槛通过。
  - `CONDITIONALLY_READY`：分数 `>= 75` 且无阻断级缺陷。
  - `NOT_READY`：分数 `< 75` 或存在阻断级缺陷。
- 阈值和权重必须支持按租户、资产类型和业务域配置，并保留版本。

## 4. 功能需求

### AR-001 评估

系统根据指定 Context 版本创建 Assessment，输出总分、维度分数、硬门槛结果、缺陷清单、证据引用和整改建议。

### AR-002 Enablement

缺陷整改必须转化为 Enablement Skill，例如补充 Owner、生成 Glossary、修复质量规则或重建血缘。整改完成后重新验证和评分。

### AR-003 Publish

Publisher 必须生成 AI Metadata、注册 AI Asset、发布 Context、写入版本、发送 `AI_READY_PUBLISHED` 事件并通知消费者。发布不是简单修改布尔字段。

### AR-004 撤回

当关键 Schema、质量、权限或策略状态失效时，系统可自动撤回发布、使缓存失效并发送 `AI_ASSET_UNPUBLISHED` 事件。

## 5. Context API

```http
GET  /api/ai/assets/{assetKey}/context
POST /api/ai-ready/assets/{assetKey}:assess
GET  /api/ai-ready/assets/{assetKey}
POST /api/ai-ready/assets/{assetKey}:publish
POST /api/ai-ready/assets/{assetKey}:unpublish
```

Context 响应必须包含资产标识、Schema、语义、质量、血缘、Owner、Classification、Policy、Usage、AI-Ready 分数、版本和来源时间。

## 6. 验收标准

- 关键维度缺失时资产不会被发布为 `AI_READY`。
- 评分结果可解释到维度、证据和缺陷。
- 通过整改 Skill 修复后可以重新评估并发布。
- 发布后 AI 消费者可通过 Context API 获得结构化上下文。
- 资产关键状态失效时可自动撤回并通知消费者。
