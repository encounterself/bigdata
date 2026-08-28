# 阶段 8：MLflow 与长期学习治理 PRD

## 1. 目标

将每次治理闭环记录为可追踪的 Governance Run，使用 MLflow 管理 AI 决策、Prompt、模型、执行和验证结果，并基于历史数据开展评估、预测和反馈学习。

## 2. Governance Run

每次 Issue 或 Migration 触发的治理闭环必须关联一个 Governance Run，至少记录：

- Tenant、Asset、Issue、Policy 和事件引用。
- Evidence、Context 版本、Prompt 版本、Model 版本。
- Intent、Risk、Confidence、Actions、审批记录。
- Skill、Capability、Execution、Verification 和最终结果。
- Token、Duration、Cost、Retry、Rollback 和人工覆盖。

## 3. MLflow Integration

- 通过 Integration Service 统一创建 Run、记录参数、指标、Artifact 和状态。
- 业务代码不得散落 MLflow SDK 调用。
- 运行记录必须支持从 RayFlow Run 跳转到 MLflow Run，也支持从模型评估回溯到资产和 Issue。
- 记录 Decision Accuracy、Risk Accuracy、修复成功率、误报率、平均治理时长、Token 成本和发布成功率。

## 4. AI 评估

- Prompt Regression：同一证据集比较 Prompt 版本变化。
- Decision Accuracy：人工确认与 AI Intent/Skill 的一致性。
- Risk Accuracy：预测风险与实际影响的一致性。
- Remediation Success：动作完成且 Verification 通过的比例。
- Safety Evaluation：越权动作、敏感数据泄漏、结构化输出失败和绕过审批。

评估数据集必须脱敏、版本化、可复现；评估失败时禁止将新模型自动提升为默认模型。

## 5. Prediction Governance

```text
Prediction Scheduler
→ Feature Extraction
→ MLflow Model Inference
→ Threshold Check
→ Governance Event
→ Detection / Decision / Action
```

首批预测场景：Paimon 文件增长、碎片化趋势、存储成本、CDC 延迟、质量下降和 AI-Ready 失效预测。

预测结果必须包含预测时间、目标时间窗、特征版本、模型版本、置信度、阈值和证据。预测本身不得直接执行高风险动作，只能创建事件或建议。

## 6. 反馈学习

- 用户接受、修改、拒绝和误报标记进入 Feedback。
- Feedback 关联 Decision、证据、模型和最终 Verification 结果。
- 反馈用于 Prompt 优化、模型评估、Skill 规则调整和预测阈值校准。
- 自动学习不得修改生产策略或 Capability 权限，必须经过版本发布和审批。

## 7. 接口

```http
GET  /api/governance/runs/{runId}
GET  /api/governance/runs/{runId}/trace
POST /api/governance/decisions/{decisionId}:feedback
GET  /api/governance/metrics
GET  /api/predictions
POST /api/predictions/{predictionId}:acknowledge
```

## 8. 验收标准

- 一次完整 Paimon 治理闭环能生成一个完整 Governance Run。
- Run 可追踪到证据、Prompt、模型、Skill、Capability、Verification 和 AI-Ready 发布。
- MLflow 记录失败不阻断核心治理结果，但必须产生告警并可补偿写入。
- 模型和 Prompt 评估具备版本、数据集和指标，低于门槛时不能自动上线。
- 预测阈值突破后能产生治理事件，但遵循现有策略和审批边界。
- 人工反馈可以被查询、统计，并用于后续离线评估。
