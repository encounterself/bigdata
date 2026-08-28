# 阶段 3：Migration Engine PRD

## 1. 目标

提供从传统企业数据源迁移到 Fluss + Flink + Paimon + Doris 的完整产品闭环，并在迁移结束后重建 Metadata、验证数据一致性并进入 AI-Ready 评估。当前仓库已有的 StarRocks 命名实现只能作为 Doris 适配基础，不能改变产品目标和用户可见命名。

## 2. 迁移模式

- 批量回填：适用于历史数据初始化。
- CDC 增量：通过 Flink CDC 维持源端到目标端同步。
- 双写/影子运行：用于切换前并行校验。
- 分批切换：按表、业务域或租户逐步切换。

## 3. 流程

```text
DISCOVER → PROFILE → MAP → PLAN → BACKFILL → CDC_SYNC
→ VALIDATE → APPROVE_CUTOVER → CUTOVER → RECONSTRUCT_METADATA
→ AI_READY_ASSESS → PUBLISH
```

任何阶段失败必须保留检查点、错误明细和可恢复操作；切换前必须具备回滚方案。

## 4. 功能需求

### MG-001 源端盘点

登记源连接、库表、列、主键、增量字段、数据量、访问模式、敏感分类和负责人。盘点结果进入统一 Metadata Plane。

### MG-002 Profiling 与映射

计算数据量、空值率、基数、分布、更新频率和异常值，生成源列到目标列的映射。映射必须支持人工修改、版本化和审批。

### MG-003 执行计划

迁移计划必须声明目标平台、分区、主键、并发、批次、窗口、保留策略、验证规则和回滚策略。

### MG-004 回填与 CDC

展示批次进度、吞吐、失败记录、延迟、重试和 Checkpoint。CDC 延迟超过阈值时触发 Governance Event 并阻止切换。

### MG-005 校验与切换

至少支持行数、聚合值、抽样记录、Schema、延迟和业务规则校验。切换需要按风险等级审批，并记录切换前后版本。

### MG-006 回滚

支持取消迁移、停止 CDC、恢复到切换前目标版本和重新打开 Issue。回滚结果必须经过 Verification。

## 5. 迁移任务契约

```json
{
  "migrationId": "mig_123",
  "tenantId": 1,
  "source": { "type": "MYSQL", "connectionId": 10 },
  "target": { "type": "PAIMON", "catalogId": 20 },
  "assets": ["customer", "order"],
  "mode": "BACKFILL_AND_CDC",
  "mappingVersion": 3,
  "validationContractId": "migration-default-v1",
  "cutover": { "approvalRequired": true, "window": "02:00-04:00" }
}
```

## 6. 接口

```http
POST /api/migrations
GET  /api/migrations/{migrationId}
POST /api/migrations/{migrationId}:profile
POST /api/migrations/{migrationId}:validate-mapping
POST /api/migrations/{migrationId}:start
POST /api/migrations/{migrationId}:pause
POST /api/migrations/{migrationId}:cutover
POST /api/migrations/{migrationId}:rollback
GET  /api/migrations/{migrationId}/logs
```

## 7. 验收标准

- 可完成至少一组 Legacy 表到 Paimon 或 Doris 的回填和 CDC；Doris 目标必须完成连接、Schema、数据校验和切换契约验收。
- Schema 映射、数据差异、CDC 延迟和失败记录可追踪。
- 未通过校验或未获得切换审批时不可切换。
- 切换失败可暂停并回滚，回滚后目标数据和 Metadata 状态一致。
- 迁移完成后自动触发 Metadata 重建和 AI-Ready 评估。
