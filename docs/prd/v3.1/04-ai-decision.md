# 阶段 4：AI Decision PRD

## 1. 目标

让 AI 基于确定性证据完成问题理解、风险判断、Intent 识别和 Skill 推荐，但不允许 AI 直接执行底层 Capability。

## 2. 输入与输出

### 输入

- GovernanceIssue。
- AssetContext。
- 命中策略和约束。
- 历史 Governance Run 摘要。
- 当前用户、租户和审批上下文。

### 输出

```json
{
  "decisionId": "dec_123",
  "issueId": "issue_123",
  "intent": "OPTIMIZE",
  "recommendedSkill": "paimon_storage_optimization",
  "risk": "LOW",
  "confidence": 0.92,
  "reasoning": "基于碎片率、文件数量和活跃读者证据判断。",
  "actions": [
    { "capability": "paimon.snapshot.cleanup", "params": { "retainCount": 30 } }
  ],
  "requiresApproval": false,
  "evidenceRefs": ["ev_1", "ev_2"]
}
```

## 3. 功能需求

### AD-001 统一模型网关

通过统一接口接入 Claude 和 OpenAI-compatible 模型。业务域不得直接依赖供应商 SDK。每次调用记录模型、Prompt、版本、Token、延迟、成本和结果状态。

### AD-002 Context Assembly

只向模型提供完成决策所需的最小证据。敏感字段默认脱敏，未经授权不得发送原始数据。上下文必须有最大大小、截断策略和来源引用。

### AD-003 结构化输出

模型输出必须通过 JSON Schema 校验。非法 JSON、未知 Intent、未知 Skill、超权限 Capability、缺失证据或低置信度时进入人工复核，不得自动执行。

### AD-004 风险决策

风险由规则基线、动作危险度、资产敏感级别、影响范围和 AI 置信度共同决定。AI 不得降低策略规定的最低风险级别。

### AD-005 人工纠正

用户可接受、修改、拒绝或升级 AI 决策。修改结果记录为反馈样本，并关联原始证据和模型版本。

## 4. Intent

首批 Intent：`OBSERVE`、`EXPLAIN`、`OPTIMIZE`、`REMEDIATE`、`MIGRATE`、`BLOCK`、`ESCALATE`、`PUBLISH`。

## 5. 接口

```http
POST /api/governance/issues/{issueId}:understand
POST /api/governance/issues/{issueId}:decide
GET  /api/governance/decisions/{decisionId}
POST /api/governance/decisions/{decisionId}:accept
POST /api/governance/decisions/{decisionId}:override
POST /api/governance/decisions/{decisionId}:reject
```

## 6. 验收标准

- AI 输出始终通过 Schema、权限和策略校验。
- AI 无法绕过审批或直接执行底层写操作。
- 每项建议都能引用具体证据、模型版本和 Prompt 版本。
- 模型不可用时系统保留确定性检测结果，并允许人工决策。
- 高风险、敏感资产和低置信度决策自动进入审批或升级队列。
