package com.rayflow.server.metadata.repository;

import com.rayflow.server.metadata.model.entity.AssetLineageEdge;
import com.rayflow.server.service.TenantAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MetadataLineageRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;

    @Transactional
    public AssetLineageEdge upsert(AssetLineageEdge edge) {
        long tenantId = tenantAccessService.requireCurrentTenantId();
        String sql = "INSERT INTO rf_asset_lineage_edge (tenant_id, upstream_asset_id, downstream_asset_id, edge_type, column_mapping_json, "
                + "job_id, job_run_key, source, source_event_id, event_time, parse_status, resolution_status, observed_at, confidence, status, details_json) "
                + "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, COALESCE(?, 'RESOLVED'), COALESCE(?, 'RESOLVED'), ?, COALESCE(?, 1.0), COALESCE(?, 'ACTIVE'), ?::jsonb) "
                + "ON CONFLICT (tenant_id, upstream_asset_id, downstream_asset_id, edge_type, source, COALESCE(source_event_id, '')) "
                + "WHERE status = 'ACTIVE' AND deleted = 0 DO UPDATE SET column_mapping_json = EXCLUDED.column_mapping_json, job_id = EXCLUDED.job_id, "
                + "job_run_key = EXCLUDED.job_run_key, event_time = EXCLUDED.event_time, parse_status = EXCLUDED.parse_status, resolution_status = EXCLUDED.resolution_status, "
                + "observed_at = EXCLUDED.observed_at, confidence = EXCLUDED.confidence, details_json = EXCLUDED.details_json, updated_at = CURRENT_TIMESTAMP RETURNING *";
        return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(AssetLineageEdge.class), tenantId, edge.getUpstreamAssetId(), edge.getDownstreamAssetId(),
                edge.getEdgeType(), edge.getColumnMappingJson(), edge.getJobId(), edge.getJobRunKey(), edge.getSource(), edge.getSourceEventId(), edge.getEventTime(),
                edge.getParseStatus(), edge.getResolutionStatus(), edge.getObservedAt(), edge.getConfidence(), edge.getStatus(), edge.getDetailsJson());
    }
}
