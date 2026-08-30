-- RayFlow v3.1 Phase 1 Metadata Plane contract.
-- All metadata writes must include tenant_id and deleted = 0 predicates.

CREATE TABLE IF NOT EXISTS rf_asset_owner (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES rf_tenant(id),
    owner_type   VARCHAR(32) NOT NULL,
    owner_key    VARCHAR(256) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    email        VARCHAR(256),
    source       VARCHAR(128) NOT NULL,
    is_primary   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_metadata_collection_run (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES rf_tenant(id),
    run_key        VARCHAR(128) NOT NULL,
    run_type       VARCHAR(32) NOT NULL,
    status         VARCHAR(32) NOT NULL,
    requested_by   BIGINT REFERENCES rf_user(id),
    platform       VARCHAR(32),
    connection_id  BIGINT,
    asset_id       BIGINT,
    requested_at   TIMESTAMPTZ NOT NULL,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    attempt        INT NOT NULL DEFAULT 0,
    max_attempts   INT NOT NULL DEFAULT 3,
    total_items    INT NOT NULL DEFAULT 0,
    success_items  INT NOT NULL DEFAULT 0,
    failed_items   INT NOT NULL DEFAULT 0,
    error_summary  TEXT,
    parameters_json JSONB,
    result_json    JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset (
    id                    BIGSERIAL PRIMARY KEY,
    tenant_id             BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_key             VARCHAR(512) NOT NULL,
    platform              VARCHAR(32) NOT NULL,
    connection_id         BIGINT,
    namespace             VARCHAR(512) NOT NULL,
    name                  VARCHAR(256) NOT NULL,
    asset_type            VARCHAR(32) NOT NULL,
    status                VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description           TEXT,
    source_locator        VARCHAR(1024),
    owner_id              BIGINT REFERENCES rf_asset_owner(id),
    last_observed_at      TIMESTAMPTZ,
    last_collection_run_id BIGINT REFERENCES rf_metadata_collection_run(id),
    schema_version        INT NOT NULL DEFAULT 0,
    metadata_version      BIGINT NOT NULL DEFAULT 0,
    tags_json             JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_rf_asset_status CHECK (status IN ('ACTIVE', 'STALE', 'DELETED', 'ERROR', 'PENDING', 'UNRESOLVED'))
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_metadata_collection_run_asset'
    ) THEN
        ALTER TABLE rf_metadata_collection_run
            ADD CONSTRAINT fk_metadata_collection_run_asset
            FOREIGN KEY (asset_id) REFERENCES rf_asset(id);
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS rf_asset_context_version (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id           BIGINT NOT NULL REFERENCES rf_asset(id),
    version_no         BIGINT NOT NULL,
    context_status     VARCHAR(32) NOT NULL,
    observed_at        TIMESTAMPTZ NOT NULL,
    source             VARCHAR(128) NOT NULL,
    confidence         NUMERIC(5,4) NOT NULL DEFAULT 1.0 CHECK (confidence BETWEEN 0 AND 1),
    freshness          VARCHAR(32) NOT NULL,
    context_json       JSONB NOT NULL,
    change_summary_json JSONB,
    collection_run_id  BIGINT REFERENCES rf_metadata_collection_run(id),
    is_current         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset_column (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id           BIGINT NOT NULL REFERENCES rf_asset(id),
    context_version_id BIGINT NOT NULL REFERENCES rf_asset_context_version(id),
    ordinal_position   INT NOT NULL,
    column_name        VARCHAR(256) NOT NULL,
    data_type          VARCHAR(256) NOT NULL,
    source_data_type   VARCHAR(256),
    nullable           BOOLEAN NOT NULL DEFAULT TRUE,
    default_expression TEXT,
    comment            TEXT,
    is_primary_key     BOOLEAN NOT NULL DEFAULT FALSE,
    is_partition_key   BOOLEAN NOT NULL DEFAULT FALSE,
    is_bucket_key      BOOLEAN NOT NULL DEFAULT FALSE,
    classification     VARCHAR(64),
    column_stats_json  JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset_statistics (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id           BIGINT NOT NULL REFERENCES rf_asset(id),
    context_version_id BIGINT NOT NULL REFERENCES rf_asset_context_version(id),
    observed_at        TIMESTAMPTZ NOT NULL,
    row_count          NUMERIC(38,0),
    data_size_bytes    NUMERIC(38,0),
    file_count         BIGINT,
    snapshot_id        VARCHAR(128),
    partition_count    BIGINT,
    null_count         NUMERIC(38,0),
    null_ratio         NUMERIC(9,6) CHECK (null_ratio BETWEEN 0 AND 1),
    distinct_count     NUMERIC(38,0),
    freshness_at       TIMESTAMPTZ,
    stats_json         JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset_quality_snapshot (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id           BIGINT NOT NULL REFERENCES rf_asset(id),
    context_version_id BIGINT REFERENCES rf_asset_context_version(id),
    observed_at        TIMESTAMPTZ NOT NULL,
    quality_status     VARCHAR(32) NOT NULL,
    score              NUMERIC(7,4) CHECK (score BETWEEN 0 AND 100),
    completeness_score NUMERIC(7,4),
    freshness_score    NUMERIC(7,4),
    validity_score     NUMERIC(7,4),
    uniqueness_score   NUMERIC(7,4),
    rule_count         INT NOT NULL DEFAULT 0,
    failed_rule_count  INT NOT NULL DEFAULT 0,
    details_json       JSONB,
    source             VARCHAR(128) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset_lineage_edge (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES rf_tenant(id),
    upstream_asset_id    BIGINT NOT NULL REFERENCES rf_asset(id),
    downstream_asset_id  BIGINT NOT NULL REFERENCES rf_asset(id),
    edge_type            VARCHAR(32) NOT NULL,
    column_mapping_json  JSONB,
    job_id               BIGINT REFERENCES rf_flink_job(id),
    job_run_key          VARCHAR(256),
    source               VARCHAR(128) NOT NULL,
    source_event_id      VARCHAR(256),
    event_time           TIMESTAMPTZ,
    parse_status         VARCHAR(32) NOT NULL DEFAULT 'RESOLVED',
    resolution_status    VARCHAR(32) NOT NULL DEFAULT 'RESOLVED',
    observed_at          TIMESTAMPTZ NOT NULL,
    confidence           NUMERIC(5,4) NOT NULL DEFAULT 1.0 CHECK (confidence BETWEEN 0 AND 1),
    status               VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    details_json         JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_rf_asset_lineage_no_self_edge CHECK (upstream_asset_id <> downstream_asset_id)
);

CREATE TABLE IF NOT EXISTS rf_asset_usage (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id       BIGINT NOT NULL REFERENCES rf_asset(id),
    consumer_type  VARCHAR(32) NOT NULL,
    consumer_key   VARCHAR(512) NOT NULL,
    access_type    VARCHAR(32) NOT NULL,
    first_seen_at  TIMESTAMPTZ NOT NULL,
    last_seen_at   TIMESTAMPTZ NOT NULL,
    access_count   BIGINT NOT NULL DEFAULT 0,
    source         VARCHAR(128) NOT NULL,
    details_json   JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset_classification (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id       BIGINT NOT NULL REFERENCES rf_asset(id),
    classification VARCHAR(64) NOT NULL,
    source         VARCHAR(128) NOT NULL,
    confidence     NUMERIC(5,4) NOT NULL DEFAULT 1.0 CHECK (confidence BETWEEN 0 AND 1),
    reason         TEXT,
    observed_at    TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset_lifecycle (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id         BIGINT NOT NULL REFERENCES rf_asset(id),
    lifecycle_status VARCHAR(32) NOT NULL,
    effective_at     TIMESTAMPTZ NOT NULL,
    retire_at        TIMESTAMPTZ,
    source           VARCHAR(128) NOT NULL,
    reason           TEXT,
    is_current       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_glossary_term (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES rf_tenant(id),
    term           VARCHAR(256) NOT NULL,
    definition     TEXT NOT NULL,
    synonyms_json  JSONB,
    owner_id       BIGINT REFERENCES rf_asset_owner(id),
    status         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_asset_glossary (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT NOT NULL REFERENCES rf_tenant(id),
    asset_id         BIGINT NOT NULL REFERENCES rf_asset(id),
    glossary_term_id  BIGINT NOT NULL REFERENCES rf_glossary_term(id),
    column_name      VARCHAR(256),
    source           VARCHAR(128) NOT NULL,
    confidence       NUMERIC(5,4) NOT NULL DEFAULT 1.0 CHECK (confidence BETWEEN 0 AND 1),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_metadata_collection_item (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES rf_tenant(id),
    run_id         BIGINT NOT NULL REFERENCES rf_metadata_collection_run(id),
    asset_id       BIGINT REFERENCES rf_asset(id),
    target_key     VARCHAR(512) NOT NULL,
    status         VARCHAR(32) NOT NULL,
    attempt        INT NOT NULL DEFAULT 0,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    observed_at    TIMESTAMPTZ,
    error_code     VARCHAR(64),
    error_message  TEXT,
    result_json    JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_metadata_event (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT NOT NULL REFERENCES rf_tenant(id),
    event_id          VARCHAR(256) NOT NULL,
    version           INT NOT NULL DEFAULT 1,
    event_type        VARCHAR(64) NOT NULL,
    source            VARCHAR(128) NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    received_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    asset_key         VARCHAR(512),
    processing_status  VARCHAR(32) NOT NULL,
    correlation_id    VARCHAR(128),
    payload_json      JSONB NOT NULL,
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS rf_metadata_event_delivery (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES rf_tenant(id),
    event_id       BIGINT NOT NULL REFERENCES rf_metadata_event(id),
    consumer_name  VARCHAR(128) NOT NULL,
    status         VARCHAR(32) NOT NULL,
    attempt        INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_tenant_key
    ON rf_asset (tenant_id, asset_key) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_context_version
    ON rf_asset_context_version (tenant_id, asset_id, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_context_current
    ON rf_asset_context_version (tenant_id, asset_id) WHERE is_current = TRUE AND deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_column_name
    ON rf_asset_column (tenant_id, context_version_id, column_name) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_column_position
    ON rf_asset_column (tenant_id, context_version_id, ordinal_position) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_lineage_current_edge
    ON rf_asset_lineage_edge (
        tenant_id, upstream_asset_id, downstream_asset_id, edge_type, source, COALESCE(source_event_id, '')
    ) WHERE status = 'ACTIVE' AND deleted = 0;
-- DAO upserts must target this partial expression index and use ON CONFLICT DO UPDATE
-- for latest-wins current-edge-set semantics; source_event_id remains replay provenance.
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_usage
    ON rf_asset_usage (tenant_id, asset_id, consumer_type, consumer_key, access_type, source) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_owner
    ON rf_asset_owner (tenant_id, owner_type, owner_key) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_classification
    ON rf_asset_classification (tenant_id, asset_id, classification, source) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_lifecycle_current
    ON rf_asset_lifecycle (tenant_id, asset_id) WHERE is_current = TRUE AND deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_glossary_term
    ON rf_glossary_term (tenant_id, LOWER(term)) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_asset_glossary
    ON rf_asset_glossary (tenant_id, asset_id, glossary_term_id, COALESCE(column_name, '')) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_metadata_collection_run
    ON rf_metadata_collection_run (tenant_id, run_key) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_metadata_collection_item
    ON rf_metadata_collection_item (tenant_id, run_id, target_key) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_metadata_event
    ON rf_metadata_event (tenant_id, event_id, source) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_rf_metadata_event_delivery
    ON rf_metadata_event_delivery (tenant_id, event_id, consumer_name) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_rf_asset_tenant_type_status
    ON rf_asset (tenant_id, asset_type, status, deleted);
CREATE INDEX IF NOT EXISTS idx_rf_asset_namespace_name
    ON rf_asset (tenant_id, namespace, name, deleted);
CREATE INDEX IF NOT EXISTS idx_rf_asset_observed
    ON rf_asset (tenant_id, last_observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_asset_source
    ON rf_asset (tenant_id, platform, connection_id, deleted);
CREATE INDEX IF NOT EXISTS idx_rf_asset_context_history
    ON rf_asset_context_version (tenant_id, asset_id, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_asset_context_source
    ON rf_asset_context_version (tenant_id, source);
CREATE INDEX IF NOT EXISTS idx_rf_asset_column_asset
    ON rf_asset_column (tenant_id, asset_id, ordinal_position);
CREATE INDEX IF NOT EXISTS idx_rf_asset_column_classification
    ON rf_asset_column (tenant_id, classification);
CREATE INDEX IF NOT EXISTS idx_rf_asset_statistics_asset
    ON rf_asset_statistics (tenant_id, asset_id, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_asset_statistics_snapshot
    ON rf_asset_statistics (tenant_id, snapshot_id);
CREATE INDEX IF NOT EXISTS idx_rf_asset_quality_asset
    ON rf_asset_quality_snapshot (tenant_id, asset_id, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_asset_quality_status
    ON rf_asset_quality_snapshot (tenant_id, quality_status);
CREATE INDEX IF NOT EXISTS idx_rf_asset_lineage_upstream
    ON rf_asset_lineage_edge (tenant_id, upstream_asset_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_rf_asset_lineage_downstream
    ON rf_asset_lineage_edge (tenant_id, downstream_asset_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_rf_asset_lineage_event
    ON rf_asset_lineage_edge (tenant_id, source_event_id);
CREATE INDEX IF NOT EXISTS idx_rf_asset_usage_asset
    ON rf_asset_usage (tenant_id, asset_id, last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_asset_usage_consumer
    ON rf_asset_usage (tenant_id, consumer_key);
CREATE INDEX IF NOT EXISTS idx_rf_asset_owner_display
    ON rf_asset_owner (tenant_id, display_name);
CREATE INDEX IF NOT EXISTS idx_rf_asset_owner_key
    ON rf_asset_owner (tenant_id, owner_key);
CREATE INDEX IF NOT EXISTS idx_rf_asset_classification_name
    ON rf_asset_classification (tenant_id, classification);
CREATE INDEX IF NOT EXISTS idx_rf_asset_lifecycle_status
    ON rf_asset_lifecycle (tenant_id, lifecycle_status);
CREATE INDEX IF NOT EXISTS idx_rf_asset_glossary_asset
    ON rf_asset_glossary (tenant_id, asset_id);
CREATE INDEX IF NOT EXISTS idx_rf_asset_glossary_term
    ON rf_asset_glossary (tenant_id, glossary_term_id);
CREATE INDEX IF NOT EXISTS idx_rf_collection_run_status
    ON rf_metadata_collection_run (tenant_id, status, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_collection_run_asset
    ON rf_metadata_collection_run (tenant_id, asset_id, requested_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_collection_item_status
    ON rf_metadata_collection_item (tenant_id, run_id, status);
CREATE INDEX IF NOT EXISTS idx_rf_collection_item_asset
    ON rf_metadata_collection_item (tenant_id, asset_id);
CREATE INDEX IF NOT EXISTS idx_rf_metadata_event_status
    ON rf_metadata_event (tenant_id, processing_status, received_at);
CREATE INDEX IF NOT EXISTS idx_rf_metadata_event_asset
    ON rf_metadata_event (tenant_id, asset_key, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_metadata_event_type
    ON rf_metadata_event (tenant_id, event_type, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_rf_metadata_delivery_consumer
    ON rf_metadata_event_delivery (tenant_id, consumer_name, status);
