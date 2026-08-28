# 阶段 6：Verification Engine PRD

## 1. 目标

把“动作已执行”与“治理目标已达成”区分开，通过可配置的 Verification Contract 判断结果，并驱动完成、重试、回滚或升级。

## 2. 验证类型

- Metadata Assert：Schema、Owner、Classification、Snapshot 等元数据断言。
- SQL Assert：行数、聚合、分布和业务规则查询。
- API Assert：外部平台返回状态、版本或资源状态。
- Metric Assert：质量、延迟、文件数、存储量和成本指标。
- Lineage Assert：上下游关系、作业版本和血缘完整性。

## 3. Contract

```json
{
  "contractId": "paimon-cleanup-v1",
  "baseline": "pre_execution",
  "checks": [
    { "type": "METRIC", "metric": "snapshot_count", "operator": "<=", "value": 30 },
    { "type": "METRIC", "metric": "storage_bytes", "operator": "<", "baseline": "pre_execution" },
    { "type": "METADATA", "metric": "active_reader_count", "operator": ">=", "value": 0 }
  ],
  "onFailure": "RETRY_THEN_ESCALATE"
}
```

Contract 必须版本化、可解释、可审计，不能仅由自然语言描述。

## 4. 功能需求

- 执行前保存基线，执行后按 Contract 采集结果。
- 支持检查依赖、超时、重试和最终一致性等待窗口。
- 结果必须保存原始值、期望值、比较方式、数据源、采集时间和错误信息。
- 支持 `PASSED`、`FAILED`、`PARTIAL`、`INCONCLUSIVE`。
- 失败时按 Capability/Skill 配置执行重试、补偿、回滚或人工升级。
- Verification 结果必须回写 Issue、ExecutionRun 和 AI-Ready Assessment。

## 5. 接口

```http
POST /api/verifications
GET  /api/verifications/{verificationId}
POST /api/verifications/{verificationId}:retry
GET  /api/executions/{runId}/verification
```

## 6. 验收标准

- Paimon Snapshot Cleanup 能验证 Snapshot 数量、存储量和活跃读者状态。
- 检查结果可解释到具体指标，不以“成功/失败”单值替代证据。
- 临时依赖不可用时返回 `INCONCLUSIVE`，不会错误标记治理成功。
- Verification 失败能自动触发配置的重试/回滚/升级动作。
- 所有验证结果可按租户、资产、Skill 和时间查询。
