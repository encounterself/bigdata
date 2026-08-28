# 阶段 5：Skill 与 Capability Runtime PRD

## 1. 目标

将 AI 的 Intent 转换为可校验、可审批、可编译和可执行的 Skill，再由可插拔 Capability Runtime 调用 Doris、Paimon、Fluss、Flink、Metadata、Quality 和 AI-Ready 能力。

## 2. 分层模型

```text
Intent → Skill → Capability → RayFlow DAG → ExecutionRun
```

- Intent：要解决什么问题。
- Skill：完成目标所需的步骤和依赖。
- Capability：一个有明确输入、权限和验证契约的原子动作。

## 3. Skill 需求

- Skill Registry 支持名称、版本、描述、适用资产、风险等级、权限、输入 Schema、输出 Schema 和 Verification Contract。
- Skill DSL 支持步骤、依赖、条件、超时、重试、审批点和补偿动作。
- 发布前必须进行语法、Schema、Capability 存在性、权限、循环依赖和危险操作校验。
- Skill 编译器将合法 DSL 编译为 RayFlow DAG，不允许前端或 AI 直接拼接执行 DAG。

示例：

```json
{
  "skill": "paimon_storage_optimization",
  "version": 1,
  "steps": [
    { "id": "check_reader", "capability": "paimon.table.check_active_readers" },
    { "id": "cleanup", "capability": "paimon.snapshot.cleanup", "dependsOn": ["check_reader"] },
    { "id": "compaction", "capability": "paimon.compaction.trigger", "dependsOn": ["cleanup"] },
    { "id": "verify", "capability": "governance.verification.run", "dependsOn": ["cleanup", "compaction"] }
  ]
}
```

## 4. Capability 需求

每个 Capability 必须实现统一生命周期：

```text
REGISTERED → VALIDATED → ENABLED → EXECUTING → VERIFYING → COMPLETED
```

失败路径：`EXECUTING → FAILED → ROLLBACK → ESCALATED`。

Capability 必须提供：定义、输入输出 Schema、PreCheck、Execute、Verify、Rollback、权限需求、风险等级、幂等键、超时和重试策略。新增能力通过 SPI/Adapter 注册，不得在核心服务中堆叠 capability 字符串判断。

## 5. 审批与安全

- `LOW`：策略允许时自动执行。
- `MEDIUM`：单人审批。
- `HIGH`：管理员审批。
- `CRITICAL`：双人审批或强制人工执行。
- PII、生产环境、删除/覆盖/切换等动作自动提升风险等级。
- 执行前校验租户、资产归属、权限、资源锁和参数范围。

## 6. 接口

```http
GET  /api/skills
POST /api/skills
POST /api/skills/{skillKey}:validate
POST /api/skills/{skillKey}:compile
POST /api/skills/{skillKey}:execute
GET  /api/skill-runs/{runId}
GET  /api/capabilities
POST /api/capabilities/{capabilityKey}:check
```

## 7. 验收标准

- 一个合法 Skill 可以编译成可查看的 RayFlow DAG。
- 非法依赖、未知 Capability、超权限参数和未审批动作无法执行。
- Capability 失败后可重试、回滚或升级，并保留完整日志。
- 同一个幂等键重复提交不会重复执行破坏性动作。
- 首期至少有一组 Paimon Capability 和一组 Doris Capability 通过注册、校验、执行和审计验收；Fluss 按 Topic/Schema 能力完成后启用对应 Capability。
