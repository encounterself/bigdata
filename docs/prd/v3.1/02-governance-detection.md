# 阶段 2：Governance Detection PRD

## 1. 目标

基于 Metadata Plane 建立事件驱动的治理检测能力，统一处理策略、质量、统计异常和预测告警，形成可分派、可决策、可审计的 `GovernanceIssue`。

## 2. 范围

- Redis Streams Governance Event Bus。
- Policy Registry、Policy DSL、Policy Evaluator。
- Rule Detector、Quality Detector、Statistical Detector、Prediction Detector 接口。
- Issue 生命周期、优先级、证据、去重、通知和人工处置。

## 3. 事件总线

标准事件：

```json
{
  "eventId": "uuid",
  "eventType": "SCHEMA_CHANGED",
  "tenantId": 1,
  "assetKey": "tenant-1:paimon:catalog.db.table",
  "source": "PAIMON_COLLECTOR",
  "occurredAt": "2026-08-28T00:00:00Z",
  "version": 1,
  "idempotencyKey": "asset:table:schema:7",
  "payload": {}
}
```

事件必须支持版本兼容、消费确认、重试退避、死信、积压监控和按租户隔离。消费者不得依赖事件到达次数实现业务状态。

## 4. 策略需求

### GD-001 策略管理

管理员可创建、编辑、版本化、启停和回滚策略。策略必须声明作用域、条件、严重级别、动作、审批级别、有效期和适用租户。

### GD-002 规则 DSL

策略条件支持字段比较、集合匹配、范围判断、正则、存在性和 AND/OR/NOT 组合。策略发布前必须通过语法、字段、权限和危险动作校验。

示例：

```json
{
  "policyKey": "ai-pii-training-block",
  "scope": { "classification": ["PII"] },
  "conditions": [{ "field": "usage.type", "operator": "EQ", "value": "AI_TRAINING" }],
  "action": "BLOCK",
  "severity": "CRITICAL",
  "approval": "ADMIN_ONLY"
}
```

### GD-003 检测器

检测器统一输出 `GovernanceIssue`，必须携带 `issueKey`、`issueType`、`assetKey`、`tenantId`、证据、严重级别、发现时间、检测器版本和推荐处理方式。

首批问题类型包括：Schema 破坏性变更、PII 使用违规、质量阈值超限、Paimon 碎片化、过期资产、无 Owner、血缘断裂和预测阈值即将突破。

## 5. Issue 生命周期

```text
OPEN → TRIAGED → DECIDED → EXECUTING → VERIFIED → RESOLVED
```

补充状态：`IGNORED`、`FALSE_POSITIVE`、`BLOCKED`、`ESCALATED`、`REOPENED`。相同租户、资产、问题类型和证据指纹在抑制窗口内不得重复创建 Issue。

## 6. 接口

```http
GET  /api/governance/issues
GET  /api/governance/issues/{issueId}
POST /api/governance/issues/{issueId}:triage
POST /api/governance/issues/{issueId}:ignore
POST /api/governance/issues/{issueId}:decide
GET  /api/governance/policies
POST /api/governance/policies
POST /api/governance/policies/{policyKey}:validate
POST /api/governance/policies/{policyKey}:publish
```

## 7. 验收标准

- Metadata 变更可在事件总线上被消费，并触发正确检测器。
- 同一问题在抑制窗口内不会重复创建。
- 策略版本可以回溯，任何 Issue 都能定位命中的策略版本。
- Issue 详情包含可复核的原始证据，而不是只有 AI 生成文本。
- Redis、检测器或数据库短暂失败时，事件可重试且不丢失。
- 策略执行动作遵循低、中、高、关键风险的审批要求。
