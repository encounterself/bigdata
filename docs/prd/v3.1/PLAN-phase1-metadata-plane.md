# RayFlow v3.1 Phase 1：Metadata Plane 实施计划

> 目标文件：`docs/prd/v3.1/PLAN-phase1-metadata-plane.md`  
> 适用范围：RayFlow v3.1 Phase 1（Metadata Plane）  
> 关联 PRD：`docs/prd/v3.1/01-metadata-plane.md`

## A. Goal & scope

### A.1 Goal

建立统一的 Metadata Plane，把 Doris、Paimon、Fluss、Flink，以及后续接入的 OpenLineage、Usage、Migration 信息归一为可检索、可关联、可审计的 `AssetContext`。本阶段的交付重点是“事实采集、版本化保存、上下文聚合和 Catalog Explorer 展示”，为 Phase 2 治理检测、Phase 4 AI 决策和后续 AI-Ready 评估提供稳定事实基础。

核心结果：

- 以租户隔离的稳定 `assetKey` 表示资产，重复采集只更新同一逻辑资产。
- 保存资产基本信息、Schema、统计、质量快照、血缘、使用、Owner、分类、生命周期和 Glossary 关联。
- 支持全量、增量、按资产、手动刷新和失败重试的异步 collection run。
- 保存 `AssetContext` 的可追溯版本；每个上下文区块包含 `observedAt`、`source`、`confidence`。
- 首期实现 Paimon、Doris、Fluss 的最小可用采集；复用现有 Flink 能力完成 Paimon/SQL Gateway 访问。
- 提供供前端和 AI 消费的资产列表、详情、Schema、血缘、质量和 Context API。

### A.2 In scope

- Canonical Asset 模型与 PostgreSQL migration `V0.0.9__metadata_plane.sql`。
- Paimon catalog/database/table/schema/snapshot/file 信息采集。
- Doris（首期复用现有 StarRocks 连接和浏览实现）库、表、Schema 信息采集；对外产品命名使用 Doris。
- Fluss cluster/topic/schema 信息采集。
- Flink job 与已知输入/输出资产的关联，以及 OpenLineage 事件的接收、规范化、去重和落库接口。
- Asset Context 聚合、上下文版本、来源/新鲜度/置信度。
- metadata collection run、item、重试、错误摘要、幂等 Upsert。
- Owner、Classification、Lifecycle、Glossary 的基础 CRUD/关联能力。
- Catalog Explorer：资产搜索、筛选、详情、Schema、统计、质量、血缘、Owner/分类/生命周期和采集状态。
- Redis 缓存、MinIO/对象存储 URI 元数据、HDFS/Hive/Paimon/Doris/Fluss/Flink 的连接配置解析和健康检查。

### A.3 Out of scope

- 本阶段不执行治理动作（修复、脱敏、权限变更、删除、Schema 自动变更）。
- Collector 不直接写 Phase 2 治理规则结果表；只写 Metadata Plane canonical model，并发出 metadata event。
- 不在本阶段实现完整 OpenLineage producer SDK、全量 Usage 埋点平台或 Migration 执行引擎；只预留事件/适配器契约和最小关联能力。
- 不把 HDFS/Hive 的物理路径当作资产主键；物理路径只作为 source locator/observed detail 保存。
- 不为 Doris 另建一套与现有 StarRocks 连接完全重复的底层驱动；产品层、表名和 API 统一为 Doris，底层实现允许兼容 StarRocks 协议。
- 不修改 Docker Compose 默认端口来适配本机 native stack。

## B. Dependencies & prerequisites

### B.1 Existing code to reuse

**通用后端和持久化约定**

- Spring Boot 入口：`backend/rayflow-server/src/main/java/com/rayflow/server/RayFlowApplication.java`。
- 统一响应：`backend/rayflow-common/src/main/java/com/rayflow/common/result/R.java`；分页：`backend/rayflow-common/src/main/java/com/rayflow/common/result/PageResponse.java`。
- MyBatis-Plus 配置：`backend/rayflow-server/src/main/java/com/rayflow/server/config/MybatisPlusConfig.java`。
- Flyway 迁移目录：`backend/rayflow-server/src/main/resources/db/migration/`；当前最新迁移是 `V0.0.8__async_image_build_task.sql`，本阶段新增 `V0.0.9__metadata_plane.sql`。
- 租户与认证上下文：`config/SecurityConfig.java`、`config/JwtAuthenticationFilter.java`、`model/entity/Tenant.java`、`service/TenantService.java`；所有 Metadata 查询必须在 service/mapper 条件中带 `tenant_id`，不能只依赖前端参数。
- 现有实体/Mapper/Service/Controller 分层：`model/entity/`、`mapper/`、`service/`、`controller/`；新增代码应遵循现有命名和 `@TableName`/`BaseMapper` 模式。
- OpenAPI 生成输入：`backend/openapi.json`；前端生成 SDK 位于 `frontend/admin/src/shared/api/generated/`，业务代码不得手写 `/api` 路径。

**Paimon/Flink 复用点**

- `service/PaimonCatalogService.java`：Paimon catalog 注册、更新、删除和连接配置。
- `service/PaimonCatalogBrowserService.java`：通过 Flink SQL Gateway 浏览 catalog、database、table、schema、snapshot、files 和 preview。
- `controller/PaimonController.java` 及 `model/response/resource/Paimon*Response.java`：现有 Paimon API 对象和错误处理可作为 collector DTO 映射参考。
- `mapper/PaimonCatalogMapper.java` 与 `model/entity/PaimonCatalog.java`：已有 catalog/tenant 关系。
- `rayflow-flink-core/src/main/java/com/rayflow/flink/client/FlinkRestClient.java`：Flink REST 探活、集群信息和 HTTP 错误处理。
- `rayflow-flink-core/src/main/java/com/rayflow/flink/client/FlinkSqlGatewayClient.java`：SQL Gateway statement/session 执行；Metadata collector 不应自行实现 SQL Gateway HTTP 客户端。
- `service/FlinkClusterService.java`：Flink runtime 解析、版本门禁和已注册内置 runtime；当前 native Flink 1.20.0 已接受 `1.` 前缀，不能回退到仅允许 `2.`。
- `service/FlinkSqlPreviewService.java`、`service/submit/SqlGatewayFlinkJobSubmitter.java`：SQL Gateway session、异步 statement、结果轮询模式可复用。
- `service/FlinkJobStatusWatcher.java`：现有异步轮询/状态机模式可复用到 collection run watcher。

**Doris/Fluss 复用点**

- Doris 首期复用 StarRocks 兼容实现：`service/StarRocksConnectionService.java`、`service/StarRocksBrowserService.java`、`controller/StarRocksConnectionController.java`、`mapper/StarRocksConnectionMapper.java`、`model/entity/StarRocksConnection.java` 和 `model/response/resource/StarRocks*Response.java`。新增 canonical adapter 命名为 Doris，不把 StarRocks 名称泄露到 Metadata API。
- Fluss 连接和 topic：`service/FlussClusterService.java`、`service/FlussTopicService.java`、`controller/FlussClusterController.java`、`controller/FlussController.java`、`mapper/FlussClusterMapper.java`、`mapper/FlussTopicMapper.java`、`model/entity/FlussCluster.java`、`model/entity/FlussTopic.java`。
- Fluss 客户端依赖和连接配置以 `backend/rayflow-server/pom.xml`、`backend/rayflow-flink-core/pom.xml` 为准；不得把 Fluss gRPC coordinator 当作 HTTP endpoint。

**前端复用点**

- 管理端入口和布局：`frontend/admin/src/app/`、`frontend/admin/src/features/resource-center/resource-center-page.tsx`。
- 现有资源面板：`frontend/admin/src/features/resource-center/panels/paimon-connections-panel.tsx`、`fluss-connections-panel.tsx`、`starrocks-connections-panel.tsx`、`flink-runtime-panel.tsx`。
- SDK 封装：`frontend/admin/src/lib/sdk/paimon-management.ts`、`fluss-management.ts`、`starrocks-connection-management.ts`、`flink-runtime-management.ts`。
- 通用请求客户端：`frontend/admin/src/shared/api/client.ts`；生成 API：`frontend/admin/src/shared/api/generated/index.ts`；类型不得在业务组件中复制一份手写 API schema。
- UI 组件、表格、drawer/modal、状态色：`frontend/admin/src/shared/ui/`、`frontend/admin/src/shared/ui/status-tone.ts`、`frontend/admin/src/lib/date.ts`。

### B.2 Infrastructure assumptions from `集群基本配置.md`

- 本机是 `hadoop-master`，地址 `192.168.10.131`；HDFS 为 `hdfs://hadoop-master:9000`，NameNode Web 为 `192.168.10.131:9870`，不能用 `localhost:9870` 代替。
- PostgreSQL 16.4：`localhost:5432`，数据库/用户 `rayflow`/`rayflow123`；native 环境不是 README Docker 的 `5433`。
- Redis：`localhost:6379`，native 环境无密码；只能通过配置解析，不把密码写入代码或文档示例。
- MinIO：API `localhost:9010`，Console `localhost:9011`，root credentials `minioadmin`/`minioadmin`，bucket `rayflow-artifacts` 已存在；不要使用 Docker RustFS 的 `rustfsadmin`。
- Flink 1.20.0：`/opt/flink`，JobManager REST `:8081`，SQL Gateway `:8083`；SQL Gateway 配置必须放在 `/opt/flink/conf/flink-conf.yaml` 的 `sql-gateway.endpoint.rest.address/port`，单独 `sql-gateway.yaml` 不会被 daemon script 读取。
- Flink 所有 daemon（JM、TM、gateway）必须有 `HADOOP_CLASSPATH`；否则 Paimon/HDFS 会报 `org.apache.hadoop.conf.Configuration` 缺失。跨节点 worker ssh 不会传播 shell 环境，依赖 `/etc/environment` 或启动脚本设置。
- Paimon native plugin：`/opt/flink/lib/paimon-flink-1.20-2.0.0.jar`；Hive connector：`/opt/flink/lib/flink-sql-connector-hive-3.1.3_2.12-1.20.0.jar`。
- Hive 4.2.1：metastore `:9083`、HS2 `:10000`；Hive 元数据 MySQL 库只作为外部 catalog 依赖，不替代 RayFlow PostgreSQL canonical model。
- Fluss 0.9.1 coordinator：`192.168.10.131:9123`，是 gRPC/TCP endpoint，不是 HTTP；tablet server 在 slave 节点。
- 当前 native 版本与项目默认矩阵不同：项目 README/Makefile 是 Flink 2.2.1/Paimon 1.4.2，本机是 Flink 1.20.0/Paimon 2.0.0。Collector 必须通过 adapter capability/version probe 处理差异，不硬编码 README 版本。
- **Doris**：集群当前**未部署** Doris。Phase 1 目标版本 **Apache Doris 4.1.3**（2026-08-30 核查），官方下载页 `https://doris.apache.org/download/` 可达。部署拓扑、端口、JDK 风险与下载链接见文末《附录：Doris 4.1.3 部署参考》。关键风险：集群 JDK 21 未经 Doris 4.1.x 官方验证（官方推荐 JDK 17），落地前需冒烟测试；如失败需为 Doris 单独装 JDK 17。Doris/StarRocks 连接在 Phase 1 复用现有 StarRocks 实体，产品层统一显示 `DORIS`。
- 后端由 `/root/host/opt/rayflow_src/run-backend.sh` 以 exploded fat jar `/opt/rayflow_src/backend-exploded` 加 `/opt/rayflow_src/libext/*` 启动，不能恢复成 `java -jar`；Paimon HiveConf/metastore class loading 依赖该布局。
- native dev 入口是 `/root/host/opt/rayflow_src/native-dev.sh`：后端 `3001`，管理端 `8001`，admin proxy target 为 `http://127.0.0.1:3001`。不要启动 `make dev-up` Docker Compose。

### B.3 Prerequisite checklist

- PostgreSQL/Redis/MinIO、HDFS、Hive metastore、Flink JM/TM/SQL Gateway、Fluss coordinator/tablets 已启动；用 `/opt/scripts/status.sh` 检查。
- 已注册 admin tenant 的资源：Fluss `fluss-native`（`192.168.10.131:9123`）、Paimon `test-native`（`file:///data/paimon/rayflow-test`）、Paimon `test_hive`（Hive URI `thrift://hadoop-master:9083`）。Doris/StarRocks 连接需提供可用 catalog endpoint。
- 后端 `.env` 位于 `RayFlow-main/.env`，含 JWT、S3、Flink runtime 和 runner jar 路径；只通过 Spring configuration/environment resolver 读取，禁止打印 secret。
- Java 17 target、当前机器 JDK 21 可用于 Maven 构建；Node 20/pnpm 可用于 admin 检查。`python3` 不可假设存在。

## C. PostgreSQL data model

### C.1 Common conventions

- 所有表使用 `BIGSERIAL id` 主键、`created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`、`updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`、`deleted INT NOT NULL DEFAULT 0`（事件/运行表另加状态和不可变事件时间）。
- 所有租户表带 `tenant_id BIGINT NOT NULL REFERENCES rf_tenant(id)`；查询、唯一约束和索引优先包含 `tenant_id`。
- JSON payload 使用 `JSONB`；时间统一 UTC，现有项目 JDBC 映射沿用 `TIMESTAMP`，事件原始时间保留 `TIMESTAMP`。
- Canonical asset 的逻辑唯一键为 `(tenant_id, asset_key)`；物理源 locator 可以变化，不能导致新逻辑资产。
- 软删除条件统一 `deleted = 0`；部分唯一索引只约束未删除记录。

### C.2 Migration file

新增文件：`backend/rayflow-server/src/main/resources/db/migration/V0.0.9__metadata_plane.sql`。

迁移要求：可重复执行/部署安全（使用 `IF NOT EXISTS` 或明确一次性 Flyway DDL）；不改写 `scripts/init-test.sql`，不把 DDL 放入 deprecated `scripts/init-db.sql`；迁移完成后验证 `flyway_schema_history`、索引和外键。

### C.3 Tables and columns

以下是本阶段的 canonical schema。除特别说明外，每张表还包含 common audit columns：`created_at`、`updated_at`、`deleted`。

#### `rf_asset`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK `rf_tenant(id)` |
| `asset_key` | `VARCHAR(512)` | NOT NULL; canonical logical key |
| `platform` | `VARCHAR(32)` | NOT NULL; `PAIMON`, `DORIS`, `FLUSS`, `FLINK`, `HIVE`, `HDFS`, `UNKNOWN` |
| `connection_id` | `BIGINT` | Nullable; source connection/resource id |
| `namespace` | `VARCHAR(512)` | NOT NULL; catalog/cluster/database/topic namespace |
| `name` | `VARCHAR(256)` | NOT NULL |
| `asset_type` | `VARCHAR(32)` | NOT NULL; `CATALOG`, `DATABASE`, `TABLE`, `TOPIC`, `JOB`, `FILESET` |
| `status` | `VARCHAR(32)` | NOT NULL DEFAULT `ACTIVE`; `ACTIVE`, `STALE`, `DELETED`, `ERROR`, `PENDING`（placeholder 未解决/待 collector 补全）, `UNRESOLVED`（OpenLineage namespace 未映射） |
| `description` | `TEXT` | Source or user description |
| `source_locator` | `VARCHAR(1024)` | URI/endpoint/path, not identity |
| `owner_id` | `BIGINT` | Nullable; FK `rf_asset_owner(id)` after owner table exists |
| `last_observed_at` | `TIMESTAMP` | Last successful observation |
| `last_collection_run_id` | `BIGINT` | Nullable; FK to collection run |
| `schema_version` | `INT` | NOT NULL DEFAULT 0; monotonically incremented on effective schema change |
| `metadata_version` | `BIGINT` | NOT NULL DEFAULT 0; optimistic context version |
| `tags_json` | `JSONB` | Free-form source tags |

Constraints/indexes: `UNIQUE (tenant_id, asset_key) WHERE deleted = 0`; `idx_asset_tenant_type_status (tenant_id, asset_type, status, deleted)`; `idx_asset_namespace_name (tenant_id, namespace, name, deleted)`; `idx_asset_observed (tenant_id, last_observed_at DESC)`; `idx_asset_source (tenant_id, platform, connection_id, deleted)`.

#### `rf_asset_context_version`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK `rf_asset(id)` |
| `version_no` | `BIGINT` | NOT NULL; starts at 1 per asset |
| `context_status` | `VARCHAR(32)` | NOT NULL; `COMPLETE`, `PARTIAL`, `STALE`, `FAILED` |
| `observed_at` | `TIMESTAMP` | NOT NULL |
| `source` | `VARCHAR(128)` | NOT NULL; adapter/event source |
| `confidence` | `NUMERIC(5,4)` | NOT NULL DEFAULT 1.0, range 0..1 |
| `freshness` | `VARCHAR(32)` | NOT NULL; `FRESH`, `AGING`, `STALE`, `UNKNOWN` |
| `context_json` | `JSONB` | NOT NULL; immutable aggregate snapshot |
| `change_summary_json` | `JSONB` | Nullable; changed sections/fields |
| `collection_run_id` | `BIGINT` | Nullable, FK |
| `is_current` | `BOOLEAN` | NOT NULL DEFAULT TRUE |

Constraints/indexes: `UNIQUE (tenant_id, asset_id, version_no)`; at most one current version enforced by unique partial index `uk_asset_context_current (tenant_id, asset_id) WHERE is_current = TRUE`; indexes on `(tenant_id, asset_id, observed_at DESC)` and `(tenant_id, source)`.

#### `rf_asset_column`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK |
| `context_version_id` | `BIGINT` | NOT NULL, FK |
| `ordinal_position` | `INT` | NOT NULL |
| `column_name` | `VARCHAR(256)` | NOT NULL |
| `data_type` | `VARCHAR(256)` | NOT NULL; normalized display type |
| `source_data_type` | `VARCHAR(256)` | Nullable; native type |
| `nullable` | `BOOLEAN` | NOT NULL DEFAULT TRUE |
| `default_expression` | `TEXT` | Nullable |
| `comment` | `TEXT` | Nullable |
| `is_primary_key` | `BOOLEAN` | NOT NULL DEFAULT FALSE |
| `is_partition_key` | `BOOLEAN` | NOT NULL DEFAULT FALSE |
| `is_bucket_key` | `BOOLEAN` | NOT NULL DEFAULT FALSE |
| `classification` | `VARCHAR(64)` | Nullable; `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `PII`, etc. |
| `column_stats_json` | `JSONB` | Nullable; min/max/sample/cardinality details |

Constraints/indexes: `UNIQUE (tenant_id, context_version_id, column_name)`; `UNIQUE (tenant_id, context_version_id, ordinal_position)`; indexes `(tenant_id, asset_id, ordinal_position)` and `(tenant_id, classification)`.

#### `rf_asset_statistics`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK |
| `context_version_id` | `BIGINT` | NOT NULL, FK |
| `observed_at` | `TIMESTAMP` | NOT NULL |
| `row_count` | `NUMERIC(38,0)` | Nullable |
| `data_size_bytes` | `NUMERIC(38,0)` | Nullable |
| `file_count` | `BIGINT` | Nullable |
| `snapshot_id` | `VARCHAR(128)` | Nullable; Paimon snapshot/version |
| `partition_count` | `BIGINT` | Nullable |
| `null_count` | `NUMERIC(38,0)` | Nullable |
| `null_ratio` | `NUMERIC(9,6)` | Nullable, range 0..1 |
| `distinct_count` | `NUMERIC(38,0)` | Nullable |
| `freshness_at` | `TIMESTAMP` | Nullable; source data timestamp |
| `stats_json` | `JSONB` | Nullable; adapter-specific details |

Indexes: `(tenant_id, asset_id, observed_at DESC)`, `(tenant_id, snapshot_id)`, `(tenant_id, context_version_id)`.

#### `rf_asset_quality_snapshot`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK |
| `context_version_id` | `BIGINT` | Nullable, FK |
| `observed_at` | `TIMESTAMP` | NOT NULL |
| `quality_status` | `VARCHAR(32)` | NOT NULL; `UNKNOWN`, `PASS`, `WARN`, `FAIL` |
| `score` | `NUMERIC(7,4)` | Nullable, range 0..100 |
| `completeness_score` | `NUMERIC(7,4)` | Nullable |
| `freshness_score` | `NUMERIC(7,4)` | Nullable |
| `validity_score` | `NUMERIC(7,4)` | Nullable |
| `uniqueness_score` | `NUMERIC(7,4)` | Nullable |
| `rule_count` | `INT` | NOT NULL DEFAULT 0 |
| `failed_rule_count` | `INT` | NOT NULL DEFAULT 0 |
| `details_json` | `JSONB` | Nullable; Phase 2 rule-compatible payload |
| `source` | `VARCHAR(128)` | NOT NULL |

Indexes: `(tenant_id, asset_id, observed_at DESC)`, `(tenant_id, quality_status)`, `(tenant_id, context_version_id)`.

#### `rf_asset_lineage_edge`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `upstream_asset_id` | `BIGINT` | NOT NULL, FK `rf_asset(id)` |
| `downstream_asset_id` | `BIGINT` | NOT NULL, FK `rf_asset(id)` |
| `edge_type` | `VARCHAR(32)` | NOT NULL; `READS`, `WRITES`, `DERIVES`（READS ⇒ upstream=dataset, downstream=job；WRITES ⇒ upstream=job, downstream=dataset；DERIVES ⇒ 表→表；其余 COPIES/CONSUMES/PRODUCES 不启用，统一归入上述三种） |
| `column_mapping_json` | `JSONB` | Nullable; 列映射摘要（Phase 1 只存不查；列级血缘见 N1b 范围说明） |
| `job_id` | `BIGINT` | Nullable, FK `rf_flink_job(id)`（仅 RayFlow 管理 job 有值；外部 OL producer 为 NULL，见 job 链接 fallback） |
| `job_run_key` | `VARCHAR(256)` | Nullable; 外部 producer 无 job_id 时的展示 key |
| `source` | `VARCHAR(128)` | NOT NULL; `OPENLINEAGE`, `FLINK`, `MANUAL`, adapter |
| `source_event_id` | `VARCHAR(256)` | Nullable; 仅作 replay 溯源，不参与唯一性 |
| `event_time` | `TIMESTAMP` | Nullable; 源事件发生时间（PRD MD-003 要求），与 `observed_at`（处理时间）区分 |
| `parse_status` | `VARCHAR(32)` | NOT NULL DEFAULT `RESOLVED`; `RESOLVED`, `PLACEHOLDER`, `UNRESOLVED` |
| `observed_at` | `TIMESTAMP` | NOT NULL |
| `confidence` | `NUMERIC(5,4)` | NOT NULL DEFAULT 1.0 |
| `status` | `VARCHAR(32)` | NOT NULL DEFAULT `ACTIVE` |
| `details_json` | `JSONB` | Nullable |

Constraints/indexes: reject self-edge unless explicitly supported; **unique active edge（当前边集合语义）**: `UNIQUE (tenant_id, upstream_asset_id, downstream_asset_id, edge_type, source, COALESCE(source_event_id,'')) WHERE status='ACTIVE' AND deleted=0`，配合 `ON CONFLICT DO UPDATE job_id, job_run_key, observed_at, event_time, confidence, column_mapping_json, details_json`（latest-wins，跨 run 幂等）；FLINK 边以 `job_run_key` 作为有效幂等值；遍历索引 `(tenant_id, upstream_asset_id, status, deleted)` 与 `(tenant_id, downstream_asset_id, status, deleted)`；另加 `(tenant_id, source_event_id)` 供幂等查找。

#### `rf_asset_usage`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK |
| `consumer_type` | `VARCHAR(32)` | NOT NULL; `USER`, `JOB`, `SERVICE`, `DASHBOARD`, `UNKNOWN` |
| `consumer_key` | `VARCHAR(512)` | NOT NULL |
| `access_type` | `VARCHAR(32)` | NOT NULL; `READ`, `WRITE`, `PREVIEW`, `SCAN` |
| `first_seen_at` | `TIMESTAMP` | NOT NULL |
| `last_seen_at` | `TIMESTAMP` | NOT NULL |
| `access_count` | `BIGINT` | NOT NULL DEFAULT 0 |
| `source` | `VARCHAR(128)` | NOT NULL |
| `details_json` | `JSONB` | Nullable |

Constraints/indexes: unique `(tenant_id, asset_id, consumer_type, consumer_key, access_type, source)`; indexes `(tenant_id, asset_id, last_seen_at DESC)` and `(tenant_id, consumer_key)`.

#### `rf_asset_owner`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `owner_type` | `VARCHAR(32)` | NOT NULL; `USER`, `TEAM`, `SERVICE` |
| `owner_key` | `VARCHAR(256)` | NOT NULL |
| `display_name` | `VARCHAR(256)` | NOT NULL |
| `email` | `VARCHAR(256)` | Nullable |
| `source` | `VARCHAR(128)` | NOT NULL |
| `is_primary` | `BOOLEAN` | NOT NULL DEFAULT TRUE |

Unique `(tenant_id, owner_type, owner_key)`; indexes `(tenant_id, display_name)` and `(tenant_id, owner_key)`.

#### `rf_asset_classification`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK |
| `classification` | `VARCHAR(64)` | NOT NULL |
| `source` | `VARCHAR(128)` | NOT NULL |
| `confidence` | `NUMERIC(5,4)` | NOT NULL DEFAULT 1.0 |
| `reason` | `TEXT` | Nullable |
| `observed_at` | `TIMESTAMP` | NOT NULL |

Unique active classification `(tenant_id, asset_id, classification, source)`; index `(tenant_id, classification)`.

#### `rf_asset_lifecycle`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK |
| `lifecycle_status` | `VARCHAR(32)` | NOT NULL; `DRAFT`, `ACTIVE`, `DEPRECATED`, `RETIRED` |
| `effective_at` | `TIMESTAMP` | NOT NULL |
| `retire_at` | `TIMESTAMP` | Nullable |
| `source` | `VARCHAR(128)` | NOT NULL |
| `reason` | `TEXT` | Nullable |
| `is_current` | `BOOLEAN` | NOT NULL DEFAULT TRUE |

Unique current row `(tenant_id, asset_id) WHERE is_current = TRUE`; index `(tenant_id, lifecycle_status)`.

#### `rf_glossary_term`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `term` | `VARCHAR(256)` | NOT NULL |
| `definition` | `TEXT` | NOT NULL |
| `synonyms_json` | `JSONB` | Nullable |
| `owner_id` | `BIGINT` | Nullable, FK `rf_asset_owner(id)` |
| `status` | `VARCHAR(32)` | NOT NULL DEFAULT `ACTIVE` |

Unique `(tenant_id, lower(term))` among non-deleted rows; indexes `(tenant_id, status)` and trigram/full-text strategy may be added only if search volume requires it.

#### `rf_asset_glossary`

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | NOT NULL, FK |
| `glossary_term_id` | `BIGINT` | NOT NULL, FK |
| `column_name` | `VARCHAR(256)` | Nullable; null means asset-level association |
| `source` | `VARCHAR(128)` | NOT NULL |
| `confidence` | `NUMERIC(5,4)` | NOT NULL DEFAULT 1.0 |
| `created_at`/`updated_at`/`deleted` | common | audit/soft delete |

Unique active association `(tenant_id, asset_id, glossary_term_id, column_name)`; indexes `(tenant_id, asset_id)` and `(tenant_id, glossary_term_id)`.

#### Collection-run and collection-item tables

`rf_metadata_collection_run` tracks an asynchronous request:

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `run_key` | `VARCHAR(128)` | NOT NULL; idempotency key |
| `run_type` | `VARCHAR(32)` | NOT NULL; `FULL`, `INCREMENTAL`, `ASSET`, `MANUAL` |
| `status` | `VARCHAR(32)` | NOT NULL; `QUEUED`, `RUNNING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, `CANCELLED` |
| `requested_by` | `BIGINT` | Nullable, FK `rf_user(id)` |
| `platform` | `VARCHAR(32)` | Nullable |
| `connection_id` | `BIGINT` | Nullable |
| `asset_id` | `BIGINT` | Nullable |
| `requested_at` | `TIMESTAMP` | NOT NULL |
| `started_at`/`finished_at` | `TIMESTAMP` | Nullable |
| `attempt`/`max_attempts` | `INT` | NOT NULL DEFAULT 0/3 |
| `total_items`/`success_items`/`failed_items` | `INT` | NOT NULL DEFAULT 0 |
| `error_summary` | `TEXT` | Nullable |
| `parameters_json`/`result_json` | `JSONB` | Nullable |

Indexes: unique `(tenant_id, run_key)`; `(tenant_id, status, requested_at DESC)`; `(tenant_id, asset_id, requested_at DESC)`.

`rf_metadata_collection_item` tracks one adapter target:

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `run_id` | `BIGINT` | NOT NULL, FK |
| `asset_id` | `BIGINT` | Nullable, FK |
| `target_key` | `VARCHAR(512)` | NOT NULL |
| `status` | `VARCHAR(32)` | NOT NULL; `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `SKIPPED` |
| `attempt` | `INT` | NOT NULL DEFAULT 0 |
| `started_at`/`finished_at` | `TIMESTAMP` | Nullable |
| `observed_at` | `TIMESTAMP` | Nullable |
| `error_code` | `VARCHAR(64)` | Nullable |
| `error_message` | `TEXT` | Nullable |
| `result_json` | `JSONB` | Nullable |

Unique `(tenant_id, run_id, target_key)`; indexes `(tenant_id, run_id, status)` and `(tenant_id, asset_id)`.

#### Metadata-event tables

`rf_metadata_event` is the immutable inbox/event audit table:

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `event_id` | `VARCHAR(256)` | NOT NULL; producer idempotency key |
| `event_type` | `VARCHAR(64)` | NOT NULL; `SCHEMA_CHANGED`, `LINEAGE`, `QUALITY_OBSERVED`, `USAGE_OBSERVED`, `OWNER_CHANGED`, `LIFECYCLE_CHANGED`, `OPENLINEAGE_RUN` |
| `source` | `VARCHAR(128)` | NOT NULL |
| `occurred_at` | `TIMESTAMP` | NOT NULL |
| `received_at` | `TIMESTAMP` | NOT NULL DEFAULT CURRENT_TIMESTAMP |
| `asset_key` | `VARCHAR(512)` | Nullable |
| `processing_status` | `VARCHAR(32)` | NOT NULL; `RECEIVED`, `PROCESSED`, `DUPLICATE`, `FAILED` |
| `correlation_id` | `VARCHAR(128)` | Nullable |
| `payload_json` | `JSONB` | NOT NULL |
| `error_message` | `TEXT` | Nullable |

Unique `(tenant_id, event_id, source)`; indexes `(tenant_id, processing_status, received_at)`, `(tenant_id, asset_key, occurred_at DESC)`, `(tenant_id, event_type, occurred_at DESC)`.

`rf_metadata_event_delivery` tracks asynchronous consumers:

| Column | Type | Rules / meaning |
|---|---|---|
| `id` | `BIGSERIAL` | PK |
| `tenant_id` | `BIGINT` | NOT NULL, FK |
| `event_id` | `BIGINT` | NOT NULL, FK `rf_metadata_event(id)` |
| `consumer_name` | `VARCHAR(128)` | NOT NULL |
| `status` | `VARCHAR(32)` | NOT NULL; `PENDING`, `SUCCEEDED`, `FAILED`, `DEAD_LETTER` |
| `attempt` | `INT` | NOT NULL DEFAULT 0 |
| `last_attempt_at` | `TIMESTAMP` | Nullable |
| `error_message` | `TEXT` | Nullable |

Unique `(tenant_id, event_id, consumer_name)`; index `(tenant_id, consumer_name, status)`.

### C.4 Referential and transactional rules

- Asset upsert、context version insert、column/statistics replace 和 collection item 完成状态必须在同一 PostgreSQL transaction 中提交；失败时保留旧的 current context。
- Context version 切换使用 optimistic compare-and-set：读取 current `metadata_version`，成功后递增；冲突返回 409 或由 collector 重试。
- 事件先写 inbox，再异步处理；重复 `(tenant_id,event_id,source)` 只标记 `DUPLICATE`，不得重复产生 lineage edge/usage 计数。
- 血缘边使用当前边集合语义：同一逻辑边（`tenant_id, upstream_asset_id, downstream_asset_id, edge_type, source`）跨 run 由不同事件产出时，以 `ON CONFLICT DO UPDATE` 更新而非新增（latest-wins）；`source_event_id` 仅作溯源，不参与唯一性。NULL `source_event_id` 用 `COALESCE(...,'')` 保证 PG 唯一索引生效。
- 删除资产采用软删除；历史 context、lineage、event 不物理删除。

## D. Backend design

### D.1 Package/module layout

在 `backend/rayflow-server/src/main/java/com/rayflow/server/` 下新增：

- `metadata/controller/AssetController.java`：`/api/assets` 列表、详情、context、schema、lineage、quality、usage。
- `metadata/controller/MetadataCollectionController.java`：`/api/metadata/collect`、run/item 查询、重试/取消。
- `metadata/controller/MetadataEventController.java`：`/api/metadata/events` 和 OpenLineage webhook/ingress。
- `metadata/controller/GlossaryController.java`：Glossary term 与 asset association。
- `metadata/service/AssetService.java`、`AssetContextService.java`、`MetadataCollectionService.java`、`MetadataEventService.java`、`GlossaryService.java`。
- `metadata/service/adapter/MetadataSourceAdapter.java`、`PaimonMetadataAdapter.java`、`DorisMetadataAdapter.java`、`FlussMetadataAdapter.java`、`FlinkMetadataAdapter.java`。
- `metadata/service/adapter/MetadataAdapterRegistry.java`：按 platform/capability/version 选择 adapter。
- `metadata/service/normalize/AssetKeyNormalizer.java`、`MetadataNormalizer.java`、`OpenLineageNormalizer.java`。
- `metadata/service/async/CollectionRunExecutor.java`、`CollectionRunScheduler.java`、`CollectionRetryPolicy.java`、`MetadataEventDispatcher.java`。
- `metadata/mapper/` 下为每张表提供 mapper；`metadata/model/entity/`、`model/request/`、`model/response/` 分开存放。

如果项目当前没有 feature package 约定，也可将类平铺到既有 `controller/service/mapper/model` 目录，但必须保留 `metadata` 命名空间，避免与资源管理实体混淆。

### D.2 Adapter contracts

```java
public interface MetadataSourceAdapter {
    MetadataPlatform platform();
    AdapterCapabilities capabilities(SourceConnection connection);
    List<MetadataTarget> discoverTargets(CollectionRequest request);
    CanonicalMetadataSnapshot collect(MetadataTarget target, CollectionContext context);
    HealthProbeResult probe(SourceConnection connection);
}
```

- `CanonicalMetadataSnapshot` 只包含 canonical DTO，不直接依赖 MyBatis entity。
- Adapter 负责 source-specific pagination、SQL/REST/gRPC、版本差异和原始 locator；normalizer 负责 assetKey、类型、列类型和时间统一。
- Paimon：优先经 `PaimonCatalogBrowserService`/`FlinkSqlGatewayClient`，采集 catalog/database/table/schema/snapshots/files；filesystem warehouse 和 Hive metastore 是两个能力分支。Hive 模式必须注明默认 warehouse `/user/hive/warehouse/<table>` caveat，不假设 catalog `warehouse` option 生效。
- Doris：复用 `StarRocksBrowserService` 的连接、库表、对象 schema 和 preview 能力；canonical platform/API 为 `DORIS`。
- Fluss：复用 `FlussClusterService`/`FlussTopicService` 的资源配置，使用 Fluss 客户端/gRPC 访问 coordinator `192.168.10.131:9123`，采集 cluster/topic/schema。
- Flink：复用 `FlinkRestClient`、`FlinkSqlGatewayClient` 和 `FlinkJobStatusWatcher`，采集 job/run、SQL statement 中可解析的 source/sink 资产，并写 lineage candidate；不能把 SQL 解析失败当成 collection run 全局失败。
- Flink SQL 解析依赖必须在 PR-11 开始前冻结并纳入构建：优先采用 Calcite 或项目兼容的 `flink-sql-parser`；主用 `FlinkRestClient.getJobDetail` 返回的 job 节点类型/配置，次用解析 `rf_flink_job.content` 中保存的 SQL。JAR job 没有可依赖的 SQL 文本，Phase 1 不为 JAR job 生成 SQL 血缘。

### D.3 Controllers and API contracts

统一返回 `{code: 0, data, msg: "success"}`；所有接口从认证上下文取得 tenant，只有明确授权的 platform admin 才能跨租户，并记录 audit reason。

```http
GET  /api/assets?page=1&size=20&keyword=&platform=&asset_type=&status=
GET  /api/assets/{assetKey}
GET  /api/assets/{assetKey}/context?version=
GET  /api/assets/{assetKey}/schema
GET  /api/assets/{assetKey}/lineage?direction=both&depth=2
GET  /api/assets/{assetKey}/quality
GET  /api/assets/{assetKey}/usage
POST /api/metadata/collect
GET  /api/metadata/collect-runs/{runId}
GET  /api/metadata/collect-runs/{runId}/items
POST /api/metadata/collect-runs/{runId}:retry
POST /api/metadata/events
POST /api/metadata/events/openlineage
GET  /api/glossary/terms
POST /api/glossary/terms
PUT  /api/glossary/terms/{id}
POST /api/assets/{assetKey}/glossary
```

- `POST /api/metadata/collect` 接收 `runType`、`platform`、`connectionId`、可选 `assetKeys[]`、`incrementalCursor`、`maxAttempts`，校验后返回 `202 Accepted` + `runId`。
- 详情找不到资产返回 404；上下文 compare-and-set 冲突返回 409；源依赖不可用返回 503，但允许返回最近一次有效快照和 `stale=true`。
- 列表分页使用现有 `PageResponse` 形状 `{list,pagination:{page,size,total,pages}}`。

### D.3.1 Lineage traversal decision

- Phase 1 采用 PostgreSQL 递归 CTE 实现 `lineage?direction=upstream|downstream|both&depth=N`；由数据库按层展开并在 `depth` 达到上限时停止，不把全图加载到应用内存。仅在后续需要跨数据源图算法或超大图优化时再评估内存遍历。
- 递归 CTE 的 anchor、每一层递归 join 和最终 asset join 都必须带 `tenant_id = :tenantId`，同时过滤 `status = 'ACTIVE' AND deleted = 0`；`assetKey` 解析也必须限定当前 tenant，防止通过边或 placeholder 跨租户泄漏。
- `rf_asset_lineage_edge` 为遍历准备双向复合索引：`(tenant_id, upstream_asset_id, status, deleted)` 与 `(tenant_id, downstream_asset_id, status, deleted)`。列级血缘是 Phase 1 范围缩减项：只在 `column_mapping_json` 保存列映射摘要，不实现列级图查询或列级遍历。

### D.4 Entities, DTOs and event contracts

- Entity：`AssetEntity`、`AssetContextVersionEntity`、`AssetColumnEntity`、`AssetStatisticsEntity`、`AssetQualitySnapshotEntity`、`AssetLineageEdgeEntity`、`AssetUsageEntity`、`AssetOwnerEntity`、`AssetClassificationEntity`、`AssetLifecycleEntity`、`GlossaryTermEntity`、`AssetGlossaryEntity`、`MetadataCollectionRunEntity`、`MetadataCollectionItemEntity`、`MetadataEventEntity`、`MetadataEventDeliveryEntity`。
- Request DTO：`AssetListRequest`、`CollectMetadataRequest`、`RetryCollectionRequest`、`MetadataEventRequest`、`OpenLineageEventRequest`、`GlossaryTermRequest`、`AssetGlossaryRequest`。
- Response DTO：`AssetSummaryResponse`、`AssetDetailResponse`、`AssetContextResponse`、`AssetSchemaResponse`、`AssetLineageResponse`、`AssetQualityResponse`、`CollectionRunResponse`、`MetadataEventResponse`、`GlossaryTermResponse`。
- `AssetContextResponse` 顶层至少包含 `assetKey`、`tenantId`、`schemaVersion`、`observedAt`、`source`、`freshness`、`confidence`、`columns`、`statistics`、`quality`、`lineage`、`usage`、`ownership`、`classification`、`lifecycle`、`glossary` 和 `aiReadiness`（Phase 1 只提供可解释摘要，不计算治理决策）。

Canonical event envelope：

```json
{
  "eventId": "producer-run-123",
  "version": 1,
  "tenantId": 1,
  "eventType": "LINEAGE",
  "source": "OPENLINEAGE",
  "occurredAt": "2026-08-29T00:00:00Z",
  "correlationId": "flink-job-run-456",
  "assetKey": "tenant-1:paimon:test_native.db.orders",
  "payload": {
    "upstream": ["tenant-1:doris:warehouse.orders"],
    "downstream": ["tenant-1:fluss:fluss-native.orders"],
    "jobKey": "..."
  }
}
```

（实现时修正 JSON 示例中的格式并由 Jackson 校验。）事件处理要求：
- **eventId 派生（B2b）**：OpenLineage `RunEvent` 规范没有 `eventId` 字段——它由 `runId + eventType` 标识，且同一 run 会发 `START/RUNNING/COMPLETE/FAIL`（共享 runId）。因此 `eventId` 必须按 `sha256(tenantId + normalizedRunEventJson)` 或 `runId + eventType` 派生，真实 `runId` 存 `correlation_id`；**不能**把 `eventId` 直接映射为 `runId`，否则 `COMPLETE`（携带权威 inputs/outputs）会被 inbox 误判为 `START` 的重复而静默丢弃血缘。
- **eventType 过滤**：只从携带 `inputs`/`outputs` 的事件类型（`COMPLETE`、`FAIL`）提取血缘边；`START/RUNNING` 通常无数据集，不产生边。
- 边幂等跨同 run 生命周期事件：edge upsert 用 `ON CONFLICT DO UPDATE`（latest-wins），同 run 的 START/COMPLETE 不会产生重复边，也不会丢 `COMPLETE` 的数据集列表。
- **tenant 信任边界（N6）**：OL/metadata ingress 的 tenant **只能从认证上下文**（JWT / `X-Tenant-Slug`）派生；payload 中的 `tenantId` 与 `assetKey` 的 `tenant-{n}` 前缀仅作**校验**（不一致 → 400），绝不信任；placeholder 资产必须按已校验 tenant 创建。
- 先 inbox 去重，再规范化 asset key；解析未知资产时可创建 placeholder asset 为 `PENDING`（见 `rf_asset.status`），后续 collector 补全并 `PENDING → ACTIVE`；未映射 namespace 显式 `UNRESOLVED`。处理失败进入 delivery retry/dead-letter，不丢原始 payload。
- **envelope 对齐 Phase 2（N8）**：envelope 增加 `"version": 1` 字段，`eventId` 即 Phase 2 的幂等键（命名对齐 `idempotencyKey`），以便后续 PG-inbox → Redis Streams 替换不破坏契约。
- **OL producer 定位（N4b）**：Phase 1 仓库与集群**无任何 OpenLineage producer**——OpenLineage 在本阶段 = **摄取契约 + 提交的测试 fixtures**；真实血缘 producer 是 Flink job source/sink 路径（PR-11）。PR-09 验收必须含合成 OL 发射 fixture（重放 START+COMPLETE：① 恰好一条边集；② 重传同信封为 no-op；③ 重传不同数据集列表的 COMPLETE 更新边而非报错）。
- **job 链接 fallback（N7）**：`job_id` FK 只对 RayFlow 管理的 `rf_flink_job` 有效；外部 OpenLineage producer 不得伪造或填入不存在的 `job_id`。此时将 `job_name`/`jobKey` 规范化后快照到 `job_run_key`，API/UI 以该快照展示外部 job 链接，并标记为 external/unresolved。

### D.5 Async task model

- API 事务只创建 `rf_metadata_collection_run` + item，并立即返回 `202`；不在 HTTP 请求线程执行全量 catalog scan。
- `CollectionRunScheduler` 使用现有 Spring scheduler/executor（`config/SchedulerExecutorConfig.java`），按 tenant/run 限流；每个 item 具备独立重试和超时。
- 状态机：`QUEUED -> RUNNING -> SUCCEEDED|PARTIAL|FAILED|CANCELLED`；单 item：`QUEUED -> RUNNING -> SUCCEEDED|FAILED|SKIPPED`。
- 每次 adapter 成功：同事务写 asset、context version、columns、statistics、quality observations、lineage/usage candidates，更新 item。
- 部分源失败时保留旧 current context，run 为 `PARTIAL`；只有没有任何成功结果且不可恢复才为 `FAILED`。
- Redis 仅用于短期 lock、进度/轮询缓存和 context cache；PostgreSQL 是任务状态事实源。缓存 key 必须含 tenant：`rf:metadata:context:{tenantId}:{assetKey}:{version}`。
- 长任务支持 retry with backoff、最大尝试次数、超时、人工 retry；不允许重复 event/edge/usage 累加。

### D.6 Observability and security

- 记录 `runId`、tenant、platform、connection id、target key、adapter、attempt、duration、result/error code；日志禁止输出 JWT、密码、S3 secret、Hive credential、完整 SQL 中的 secret。
- OpenLineage ingress 必须限制 payload size、校验 tenant/source 签名或内部权限、拒绝跨租户 asset reference。
- 所有 SQL/查询使用参数绑定；assetKey/namespace 的 LIKE 查询需转义 wildcard；禁止通过 `id` endpoint 绕过 tenant 条件。

## E. Frontend changes for Catalog Explorer

### E.1 Navigation and page structure

新增 `frontend/admin/src/features/catalog-explorer/`：

- `catalog-explorer-page.tsx`：页面壳、筛选状态、URL query 同步。
- `components/asset-list.tsx`：分页表格，显示名称、类型、平台、状态、Owner、quality tone、freshness、last observed。
- `components/asset-filters.tsx`：keyword、platform、asset type、status、owner、classification、freshness。
- `components/asset-detail-drawer.tsx`：详情 header、source locator、采集操作和上下文版本选择。
- `components/asset-schema-tab.tsx`：列名、类型、nullable、PK/partition、comment、classification、column stats。
- `components/asset-statistics-tab.tsx`：row count、size、file/snapshot、null ratio、freshness。
- `components/asset-lineage-tab.tsx`：upstream/downstream、depth、source/confidence、job link。
- `components/asset-quality-tab.tsx`：quality status/score/rule summary，Phase 2 未执行的规则显示 unknown。
- `components/asset-governance-tab.tsx`：Owner、classification、lifecycle、glossary。
- `components/collection-run-panel.tsx`：触发采集、进度、item 错误、retry。
- `hooks/use-catalog-explorer-state.ts`、`api-adapters.ts`、`types.ts`、`index.ts`。

具体路由接入现有 `frontend/admin/src/app/` 导航/布局；资源连接仍留在 `resource-center`，Catalog Explorer 只消费 canonical assets。

### E.2 SDK and UX requirements

- 后端 OpenAPI 更新后运行项目既有生成流程，更新 `frontend/admin/src/shared/api/generated/`；手写 wrapper 只允许放 `frontend/admin/src/lib/sdk/metadata-management.ts`，其内部调用 generated functions。
- 支持列表空态、loading skeleton、错误/503 stale banner、无权限态、无 schema/lineage 态。
- 详情 drawer 的每个区块显示 `source`、`observedAt`、`confidence`；stale context 不得伪装成实时。
- 采集按钮提交后显示 `runId` 和 polling，不阻塞详情；成功后刷新 context，失败展示 item-level error 和 retry。
- assetKey 只用于路由/请求编码，页面显示使用 namespace/name；不把连接 credential 或完整 source locator 直接展示给普通用户。
- 租户切换复用 `stores/tenant-store.ts`、`shared/tenant/storage.ts`，切换后清空 asset/context query cache，避免跨租户缓存泄漏。

### E.3 Frontend acceptance

- 可从导航打开 Catalog Explorer，按 keyword/platform/type/status 分页搜索。
- 点击资产打开详情，至少可查看 schema、statistics、quality、lineage、ownership/classification/lifecycle/glossary。
- 能触发手动采集、显示 202/run 状态、查看失败原因和 retry。
- 在 backend 返回 503 + stale snapshot 时明确显示 stale，不清空最近有效数据。
- `make check-admin`（eslint + `tsc --noEmit`）通过；所有业务 API 使用 generated SDK。

## F. Integration points with cluster infra and env/credential resolution

| System | Integration | Resolution and safety |
|---|---|---|
| PostgreSQL | Canonical metadata, runs, events, glossary | Spring datasource from `RayFlow-main/.env`; native `localhost:5432`; Flyway owns DDL; transaction/tenant predicates required |
| Redis | Locks, progress, context cache, idempotency short TTL | `localhost:6379` native, password optional; never log value; key includes tenant and version |
| MinIO | Optional raw event/archive or large source payload references | `localhost:9010`, console `9011`, bucket `rayflow-artifacts`, credentials from env; store object URI/checksum, not secret |
| HDFS | Paimon/Hive/Fluss physical storage observation | `hdfs://hadoop-master:9000`; NameNode web `192.168.10.131:9870`; requires Hadoop classpath in Flink daemons |
| Hive | Paimon Hive metastore discovery | metastore `thrift://hadoop-master:9083`, HS2 `:10000`; use Paimon catalog/SQL Gateway; do not treat Hive MySQL schema as RayFlow metadata |
| Flink REST | Runtime probe, jobs, jar/cluster facts | built-in runtime address from `rf_flink_cluster` / `RAYFLOW_BUILTIN_FLINK_*`; native JM `:8081`; reuse `FlinkRestClient` |
| Flink SQL Gateway | Paimon catalog/table/schema/snapshot/file queries | native `:8083`; config in `/opt/flink/conf/flink-conf.yaml`; reuse `FlinkSqlGatewayClient`; HADOOP_CLASSPATH required |
| Paimon | catalog/database/table/schema/snapshot/files | native plugin Paimon 2.0.0 + Flink 1.20.0; filesystem and Hive-metastore capability branches; preserve source version |
| Doris | database/table/schema/statistics | product config/API `DORIS`; reuse StarRocks-compatible browser/connection implementation; connection secret env/ref only |
| Fluss | cluster/topic/schema and usage/lineage candidates | coordinator `192.168.10.131:9123` gRPC; use cluster resource config/client, never HTTP assumptions |

### F.1 Env/credential resolver

新增统一 `metadata/config/MetadataEndpointResolver.java` 或复用现有 configuration properties：

1. 优先使用已注册资源实体（tenant-scoped `rf_paimon_catalog`、`rf_fluss_cluster`、Doris/StarRocks connection、`rf_flink_cluster`）。
2. 对 built-in runtime 使用 `RAYFLOW_BUILTIN_FLINK_*` 的现有 bootstrap 逻辑，不从前端传入任意 endpoint 覆盖。
3. 本机 native 默认仅作为开发环境 fallback，不能写入生产数据库或把 credentials hardcode 到 Java/TS。
4. URL/credential 在 adapter 初始化时解析，日志只打印 host/port/resource id，不打印 secret/query string。
5. 依赖探活失败要返回可分类错误（DNS/TCP/auth/query/classpath/timeout），供 run item 和 UI 展示。

### F.2 Dataset → canonical assetKey 对账 registry（B2a，PR-01 冻结）

OpenLineage 数据集引用与 collector 扫描必须解析到**同一个** assetKey，否则 placeholder 永不与真实资产合并、血缘图断裂。PR-01 必须定义 per-platform 解析 registry：

- 规则：`namespace/name` 前缀 → 已注册连接/catalog。例如：
  - `paimon://<catalog>` 或 `file:///data/paimon/...` → 对应 `rf_paimon_catalog` 的 catalog 名 → `tenant-{id}:paimon:{catalog}.{db}.{table}`
  - JDBC URL / `doris://<host>:<port>/<db>` → `rf_starrocks_connection`（产品层 DORIS）→ `tenant-{id}:doris:{db}.{table}`
  - `fluss://<cluster>/<topic>` → `rf_fluss_cluster` → `tenant-{id}:fluss:{cluster}.{topic}`
- 每个已注册连接的 `namespace` 规范化规则必须与 `AssetKeyNormalizer`（PR-03）一致。
- **未映射的 namespace 显式标记 `UNRESOLVED`**（不静默），placeholder 资产状态为 `PENDING`，collector 补全后 `PENDING → ACTIVE`。
- assetKey 格式 `tenant-{tenantId}:{platform}:{namespace}.{name}` 在 **PR-03 冻结**（PR-09 依赖它），含名称大小写/特殊字符的 canonical escape 规则。
- 防 placeholder 爆炸（N5b）：稳定 key 规范化（剥离易变 segment）、`asset_key VARCHAR(512)` 硬上限、每租户 placeholder 上限、对 N 天无新观察边的 placeholder 做夜间 re-resolve/retire。

## G. Work breakdown: independently reviewable PRs

每一项都是一个独立、可打开、可审查、可回滚的 PR；按下列顺序合并，后续 PR 依赖前一项已合并的契约，但每项都必须包含自己的测试和验收证据。

### PR-01：Freeze canonical contracts and migration

**范围**：新增 `V0.0.9__metadata_plane.sql`、metadata enums/JSON contract 文档、assetKey 规范；不接入 collector/UI。  
**验收**：Flyway 在干净 PostgreSQL 和已有 V0.0.8 数据库均成功；所有表、FK、partial unique indexes、tenant predicates 与本节定义一致；migration smoke test 验证重复 asset/event/run 被拒绝或幂等。

### PR-02：Metadata entity/mapper/repository foundation

**范围**：新增 `metadata/model/entity/`、`metadata/mapper/`、分页查询和 transaction repository；实现 tenant-scoped asset upsert/current context CAS。  
**验收**：repository tests 覆盖重复 upsert、软删除、context version current 切换、跨租户不可见、并发 version conflict；`make check-backend` 相关编译通过。

### PR-03：Canonical normalizer and adapter SPI

**范围**：`MetadataSourceAdapter`、registry、`AssetKeyNormalizer`、canonical snapshot DTO、capability/version probe、统一错误码。  
**验收**：同一 source locator 在重复运行生成同一 assetKey；不同 tenant 生成不同 key；未知/缺失字段不会导致数据越界；adapter contract unit tests 通过。

### PR-04：Paimon metadata collector

**范围**：复用 `PaimonCatalogBrowserService` + `FlinkSqlGatewayClient`，采集 filesystem/Hive Paimon catalog 的 catalog/database/table/schema/snapshot/files。  
**验收**：对 `test-native` 至少采集一张表的 columns、partition、snapshot、file count/size；对 `test_hive` 采集成功且标记 `/user/hive/warehouse` caveat；gateway 不可用时旧 context 保留、item/run 正确失败。

### PR-05：Doris metadata collector

**范围**：canonical `DorisMetadataAdapter`，复用 `StarRocksConnectionService`/`StarRocksBrowserService` 的连接和库表/schema 浏览。  
**验收**：能采集 database/table/columns/comment/type/nullability；StarRocks-compatible source 在 UI/API 显示 platform `DORIS`；认证失败产生分类错误，不覆盖旧快照；部署配置不把 FE `edit_log_port` 设为与 MinIO 冲突的 9010，采用附录约定的 9012。

### PR-06：Fluss metadata collector

**范围**：复用 Fluss cluster/topic services/client，采集 coordinator、topic、schema、partition/bucket 元数据。  
**验收**：对 `fluss-native` 通过 `192.168.10.131:9123` gRPC/TCP 采集 topic/schema；错误提示明确不是 HTTP；重复采集无重复 asset。

### PR-07：Async collection-run executor and APIs

**范围**：`MetadataCollectionController`、run/item persistence、scheduler/executor、retry/cancel/status、Redis lock/progress。  
**验收**：`POST /api/metadata/collect` 在可测延迟内返回 202/runId；状态完整流转；item 独立 retry/backoff；部分失败返回 PARTIAL 并保留旧 current context；同一 runKey 幂等。

### PR-08：Asset Context aggregation and read APIs

**范围**：`AssetService`、`AssetContextService`、asset list/detail/context/schema/statistics/quality/lineage/usage API；context cache。  
**验收**：返回完整 context envelope 和每个 section 的 source/observedAt/confidence；分页形状符合现有约定；cache key tenant-scoped；依赖源不可用时返回 503 + 最近快照。

### PR-09：Metadata event inbox and OpenLineage normalization

**范围**：event/delivery tables、`MetadataEventController`、OpenLineage envelope parser、dedup、lineage edge upsert、placeholder asset。  
**验收**：使用合成 OpenLineage 发射 fixture 重放 `START/COMPLETE`，同一 event 重放只处理一次且 COMPLETE 的数据集列表可更新当前边集；合法 run 产生 upstream/downstream edge；未知资产可创建 `PENDING` placeholder，未映射 namespace 标记 `UNRESOLVED` 后补全；tenant 从 JWT 认证上下文派生，payload 自报 tenant 不一致、无效 signature 或超 payload size 被拒绝；外部 producer 无 `job_id` 时展示 `job_run_key` 快照；失败进入 retry/dead-letter。

### PR-10：Owner/classification/lifecycle/glossary

**范围**：四类模型的 service/API、asset association、变更事件和 context sections。  
**验收**：用户可维护 Owner/classification/lifecycle/glossary；当前 lifecycle/owner 约束生效；每次有效变化产生 metadata event/context change summary；跨租户关联被拒绝。

### PR-11：Flink/Usage/Migration integration candidates

**范围**：前置冻结 Calcite 或兼容的 `flink-sql-parser` 依赖；复用 `FlinkRestClient`/`FlinkSqlGatewayClient`/`FlinkJobStatusWatcher` 采集 job/run 和 SQL source/sink candidates，主用 `FlinkRestClient.getJobDetail` 的节点类型，次用解析 `rf_flink_job.content` 中的 SQL；JAR job 不生成 SQL 血缘；Usage/Migration event contract，不实现治理动作。
**验收**：Flink SQL job 能关联已存在 asset；无法解析的 SQL 记录 warning 而不让整个 run 失败；JAR job 明确无血缘且不误报；usage event 幂等累加；Migration 事件可入 inbox 但不触发执行。

### PR-12：Catalog Explorer shell and generated SDK

**范围**：OpenAPI 更新、generated SDK、`features/catalog-explorer/` 页面壳、路由/导航、列表/筛选。  
**验收**：生成 SDK 与 API schema 一致；列表分页、租户切换、loading/error/empty 可用；`make check-admin` 通过；无手写 API path。

### PR-13：Catalog Explorer detail and collection UX

**范围**：detail drawer/tabs、schema/statistics/quality/lineage/governance、collection-run panel、stale/503/retry UX。  
**验收**：用户可从列表打开完整 context；每个 section 显示 source/observedAt/confidence；触发采集并轮询 run；stale 不被误显示为实时；关键组件测试通过。

### PR-14：End-to-end native validation and operational documentation

**范围**：native stack E2E、seed fixtures、监控/告警、运行手册和本 PRD 交叉链接；不改无关服务。  
**验收**：使用 `/opt/scripts/status.sh` + `/root/host/opt/rayflow_src/native-dev.sh` 验证 PG/Redis/MinIO/HDFS/Hive/Flink/Paimon/Fluss；启动 Doris 前执行端口冲突检查，核对 9010（MinIO API）、8030、8040、9030、9012 等端口的占用与空闲状态，确认 Doris 不占用 MinIO 的 9010 且 FE 使用 `edit_log_port=9012`；完成 Paimon、Doris、Fluss 最小采集、context 浏览、合成 OpenLineage fixture dedup、租户隔离、失败重试；记录 Flink 1.20/Paimon 2.0 与项目默认版本差异。

## H. Risks, unknowns, open questions

### H.1 Risks

- **版本差异**：代码默认 Flink 2.2.1/Paimon 1.4.2，本机为 Flink 1.20.0/Paimon 2.0.0；SQL/Table API、Paimon metadata schema 和 Hive connector 行为可能改变。Adapter 必须 capability probe，E2E 不能只依赖 mock。
- **Hive/Paimon warehouse 误判**：Hive metastore catalog 的 `warehouse` option 可能不控制实际路径，native 环境已知表可能落在 `/user/hive/warehouse/<table>`；不可用路径推断 catalog identity。
- **Classloading**：后端 Paimon HiveConf 依赖 exploded jar + `/opt/rayflow_src/libext/*`；回到 `java -jar` 会造成 metastore class not found。
- **异步一致性**：采集、事件、context、缓存同时更新时可能出现旧缓存、双写或 current version 错乱；必须以 PG transaction/CAS 为事实源，Redis 只做可丢失加速层。
- **大规模 catalog**：全量扫描可能超过 HTTP/request、内存或 SQL Gateway statement timeout；需要分页、item 粒度、限流和断点 cursor。
- **跨租户泄漏**：assetKey、缓存、event payload、placeholder asset 和 lineage traversal 都可能成为绕过 tenant predicate 的路径；需要 mapper tests 和 API integration tests。
- **Doris/StarRocks 语义**：协议兼容不等于统计/权限/类型完全兼容；产品 naming 统一 Doris 但 source capability 必须可见。
- **OpenLineage 不完整**：namespace/name/dataset facets 不一定能直接映射现有资源；需要可解释的 unresolved/placeholder 状态，不应静默丢边。

### H.2 Unknowns

- 生产环境是否允许每个 tenant 访问同一个 native Flink/Hive/Fluss runtime，还是必须建立 connection-level credential/网络隔离。
- 是否需要把原始 OpenLineage payload 长期存 MinIO，还是 PostgreSQL `JSONB` 足以支撑保留周期；保留周期和脱敏策略未定。
- Doris 生产版本、JDBC/HTTP 协议、SHOW 语法和统计权限尚未冻结；需要真实 Doris fixture。
- Fluss schema API 的稳定版本、topic retention、partition/bucket 映射到 canonical statistics 的字段仍需确认。
- Usage 的第一来源是 Flink job、SQL preview、应用 query log 还是 OpenLineage run；本阶段先落事件契约，具体 producer 排期可调整。
- Quality snapshot 是仅保存外部结果，还是 Phase 2 直接执行规则并回写；本阶段只保存兼容 payload，不承诺规则执行。
- 是否需要 asset-level 与 column-level Owner/classification 的继承规则；当前模型支持 column glossary/classification，Owner 先以 asset-level 为主。
- Search 规模达到何种阈值后才引入 PostgreSQL `pg_trgm`、全文索引或独立搜索服务；Phase 1 先用 indexed keyword/prefix 查询。

### H.3 Open questions requiring decision before PR-04/PR-07

1. `assetKey` 是否正式固定为 `tenant-{tenantId}:{platform}:{namespace}.{name}`，以及名称大小写/特殊字符的 canonical escape 规则是什么？
2. Collection run 的执行器继续使用 Spring scheduler，还是接入现有 scheduler workflow 作为可视化任务？Phase 1 推荐先使用 `SchedulerExecutorConfig`，避免把元数据采集和业务调度耦合。
3. OpenLineage ingress 是否仅内部 authenticated API，还是需要签名 webhook；跨租户 producer 如何传递 tenant？
4. Doris adapter 是否允许连接实体暂时落在 StarRocks 表中，还是本阶段同时改成 `rf_doris_connection`？推荐先复用现有表，待 Doris 语义稳定后单独迁移。
5. Context JSON 是否仅作为 immutable snapshot，还是需要拆分更多查询列；推荐 canonical columns + JSONB extension，避免 Phase 1 过度建模。
6. Metadata event 是否需要 Redis Stream/Kafka/Fluss 作为 durable transport；Phase 1 推荐 PostgreSQL inbox + async executor，后续按吞吐量替换 dispatcher，不改变 event contract。

---

## 附录：Doris 4.1.3 部署参考

> 本文档追加于 2026-08-30。目标：在 RayFlow Phase 1（Metadata Plane / PR-05 Doris collector）落地 Doris 作为元数据源。以下信息来自对官方文档与发布页的核查，落地前需在目标机器上做一次安装冒烟验证。

### 版本与下载

- 最新稳定版：**Apache Doris 4.1.3**（GitHub `apache/doris` latest release，2026-07-13 发布）。
- 官方下载页：`https://doris.apache.org/download/`（本机可达）。
- 官方二进制（Apache 官方 OSS release 存储）：
  ```text
  https://apache-doris-releases.oss-accelerate.aliyuncs.com/apache-doris-4.1.3-bin-x64.tar.gz
  https://apache-doris-releases.oss-accelerate.aliyuncs.com/apache-doris-4.1.3-bin-x64-noavx2.tar.gz
  https://apache-doris-releases.oss-accelerate.aliyuncs.com/apache-doris-4.1.3-bin-arm64.tar.gz
  ```
- 校验文件：同路径追加 `.asc`（ASC 签名）与 `.sha512`。
- 备选镜像（GitHub release body 给出，但本机访问 HTTP 403，需实测）：
  ```text
  https://download.velodb.io/apache-doris-4.1.3-bin-x64.tar.gz
  https://download.velodb.io/apache-doris-4.1.3-bin-x64-noavx2.tar.gz
  ```
- 源码（Apache dist 镜像，本机 dist.apache.org 可达）：
  ```text
  https://dist.apache.org/repos/dist/release/doris/4.0/4.1.3/apache-doris-4.1.3-rc02-src.tar.gz
  ```
- ⚠️ 本机 `download.doris.apache.org` DNS 不可解析；不要把它作为下载入口写入部署脚本。

### 部署架构

- 角色：**FE**（Frontend：SQL/MySQL 入口、查询规划、元数据管理、FE 共识）+ **BE**（Backend：存储与查询执行）。Broker / Arrow Flight SQL 可选，Phase 1 不需要。
- 生产级三节点推荐（每节点一个 FE + 一个 BE）：
  ```text
  hadoop-master  : FE Follower（可选举为 Master）+ BE
  hadoop-slave1  : FE Follower + BE
  hadoop-slave2  : FE Follower + BE
  ```
- **RayFlow Phase 1 推荐初始拓扑**（保留 3 副本、简化 FE）：
  ```text
  hadoop-master  : FE Master + BE
  hadoop-slave1  : BE
  hadoop-slave2  : BE
  ```
- **最小开发部署**（可用于集成测试）：单节点 `1 FE + 1 BE`，官方明确支持；满足“注册 Doris 数据源、JDBC 枚举库/表/列、验证 collector”即可。

### 端口清单

| 组件 | 配置项 | 默认端口 | 用途 |
|---|---|---|---|
| FE | `http_port` | 8030 | FE HTTP API / Web |
| FE | `query_port` | 9030 | MySQL 协议 / JDBC |
| FE | `edit_log_port` | 9012 | FE 间 BDBJE 编辑日志；改用 9012，避免与集群 MinIO API 的 9010 冲突 |
| FE | `rpc_port` | 9020 | FE Thrift/RPC |
| BE | `be_port` | 9060 | BE Thrift（FE→BE） |
| BE | `webserver_port` | 8040 | BE HTTP |
| BE | `heartbeat_service_port` | 9050 | FE→BE 心跳 |
| BE | `brpc_port` | 8060 | FE/BE 与 BE/BE BRPC |

> 暴露给 RayFlow 后端 collector 的只需 FE `query_port`（9030，JDBC）与可选 FE `http_port`（8030）。三节点部署需在节点间放行上述 FE/BE 端口。

### JDK 兼容性（关键风险）

- 官方文档：Doris 3.0+ 使用 **JDK 17**（推荐 `jdk-17.0.10+`）；部署手册写 “JDK 17+”。
- **集群现为 JDK 21**：JDK 21 未被 Doris 4.1.x 官方明确验证。FE 是 Java 进程，BE 的 JNI 集成也依赖 Java。落地前必须在目标机做 **JDK 21 冒烟测试**；如失败，**为 Doris 单独安装 JDK 17**，不改集群全局 JDK（避免影响 Hadoop/Hive/Flink/RayFlow）。

### 目录与启动

- 解压包结构：`<DORIS_HOME>/{fe,be}`。
- FE：配置 `<DORIS_HOME>/fe/conf/fe.conf`，元数据默认 `<DORIS_HOME>/fe/doris-meta`（生产建议移到 SSD 并软链回），日志 `<DORIS_HOME>/fe/log`。
- BE：配置 `<DORIS_HOME>/be/conf/be.conf`，数据默认 `<DORIS_HOME>/be/storage`，可用 `storage_root_path=/data/doris/be1,medium:SSD` 自定义多路径。
- 启动：
  ```bash
  cd <DORIS_HOME>/fe && bin/start_fe.sh --daemon
  cd <DORIS_HOME>/be && bin/start_be.sh --daemon
  ```
- 默认账号：`root`（初始无密码或按官方文档设置），管理可用 `root` 空密码连接 9030；生产必须设置密码。

### 前置与风险

- **JDK 21 未经官方验证**（见上）。
- **CentOS 7 + 老 CPU**：若 CPU 不支持 AVX2，须用 `-x64-noavx2` 包。
- **下载源 403**：`download.velodb.io` 与部分 OSS 链接可能限流；先试官方 OSS，失败再试 velodb，并在脚本里支持 `DORIS_DOWNLOAD_URL` 覆盖。
- **与现有 StarRocks 代码的关系**：RayFlow 现有 `StarRocksConnectionService`/`StarRocksBrowserService` 复用为 Doris 适配基础；产品层统一显示 `DORIS`。Doris 4.1.x 与 StarRocks 的 JDBC 元数据/SHOW 语法存在差异，collector 的 capability/version probe 需覆盖。
- **FE 单点**：初始 1 FE + 3 BE 拓扑中 FE 是控制面单点，仅适合 Phase 1；生产需 3 FE Follower。
- **数据隔离**：Doris FE/BE 数据目录建议与 Hadoop 数据目录分盘。
