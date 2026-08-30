package com.rayflow.server.metadata.repository;

import com.rayflow.server.metadata.model.entity.AssetContextVersion;
import com.rayflow.server.service.TenantAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MetadataContextRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;

    @Transactional
    public AssetContextVersion insertCurrent(AssetContextVersion context, long expectedMetadataVersion) {
        long tenantId = tenantAccessService.requireCurrentTenantId();
        Long currentVersion;
        try {
            currentVersion = jdbcTemplate.queryForObject("SELECT metadata_version FROM rf_asset WHERE id = ? AND tenant_id = ? "
                    + "AND deleted = 0 FOR UPDATE", Long.class, context.getAssetId(), tenantId);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw new MetadataConflictException("asset " + context.getAssetId() + " is not visible to the current tenant");
        }
        if (currentVersion == null || currentVersion != expectedMetadataVersion) {
            throw new MetadataConflictException("asset context version conflict for asset " + context.getAssetId()
                    + ": expected metadata version " + expectedMetadataVersion + ", actual " + currentVersion);
        }
        jdbcTemplate.update("UPDATE rf_asset_context_version SET is_current = FALSE, updated_at = CURRENT_TIMESTAMP "
                + "WHERE tenant_id = ? AND asset_id = ? AND is_current = TRUE AND deleted = 0", tenantId, context.getAssetId());
        long versionNo = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version_no), 0) + 1 FROM rf_asset_context_version "
                + "WHERE tenant_id = ? AND asset_id = ? AND deleted = 0", Long.class, tenantId, context.getAssetId());
        String sql = "INSERT INTO rf_asset_context_version (tenant_id, asset_id, version_no, context_status, observed_at, source, "
                + "confidence, freshness, context_json, change_summary_json, collection_run_id, is_current) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, TRUE) RETURNING *";
        AssetContextVersion result = jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(AssetContextVersion.class), tenantId,
                context.getAssetId(), versionNo, context.getContextStatus(), context.getObservedAt(), context.getSource(), context.getConfidence(),
                context.getFreshness(), context.getContextJson(), context.getChangeSummaryJson(), context.getCollectionRunId());
        jdbcTemplate.update("UPDATE rf_asset SET metadata_version = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND tenant_id = ? AND deleted = 0",
                versionNo, context.getAssetId(), tenantId);
        return result;
    }

    public AssetContextVersion current(long assetId) {
        long tenantId = tenantAccessService.requireCurrentTenantId();
        return jdbcTemplate.queryForObject("SELECT * FROM rf_asset_context_version WHERE tenant_id = ? AND asset_id = ? "
                + "AND is_current = TRUE AND deleted = 0", new BeanPropertyRowMapper<>(AssetContextVersion.class), tenantId, assetId);
    }
}
