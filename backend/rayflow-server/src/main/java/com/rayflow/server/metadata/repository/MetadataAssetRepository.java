package com.rayflow.server.metadata.repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rayflow.server.metadata.model.entity.Asset;
import com.rayflow.server.service.TenantAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MetadataAssetRepository {
    private final JdbcTemplate jdbcTemplate;
    private final TenantAccessService tenantAccessService;

    @Transactional
    public Asset upsert(Asset asset) {
        long tenantId = tenantAccessService.requireCurrentTenantId();
        asset.setTenantId(tenantId);
        String sql = """
                INSERT INTO rf_asset (tenant_id, asset_key, platform, connection_id, namespace, name, asset_type,
                    status, description, source_locator, owner_id, last_observed_at, last_collection_run_id,
                    schema_version, metadata_version, tags_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'ACTIVE'), ?, ?, ?, ?, ?, COALESCE(?, 0), COALESCE(?, 0), ?::jsonb)
                ON CONFLICT (tenant_id, asset_key) WHERE deleted = 0 DO UPDATE SET
                    platform = EXCLUDED.platform, connection_id = EXCLUDED.connection_id,
                    namespace = EXCLUDED.namespace, name = EXCLUDED.name, asset_type = EXCLUDED.asset_type,
                    status = EXCLUDED.status, description = EXCLUDED.description, source_locator = EXCLUDED.source_locator,
                    owner_id = EXCLUDED.owner_id, last_observed_at = EXCLUDED.last_observed_at,
                    last_collection_run_id = EXCLUDED.last_collection_run_id, schema_version = EXCLUDED.schema_version,
                    metadata_version = rf_asset.metadata_version + 1, tags_json = EXCLUDED.tags_json,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING *
                """;
        return jdbcTemplate.queryForObject(sql, new BeanPropertyRowMapper<>(Asset.class), tenantId, asset.getAssetKey(),
                asset.getPlatform(), asset.getConnectionId(), asset.getNamespace(), asset.getName(), asset.getAssetType(),
                asset.getStatus(), asset.getDescription(), asset.getSourceLocator(), asset.getOwnerId(), asset.getLastObservedAt(),
                asset.getLastCollectionRunId(), asset.getSchemaVersion(), asset.getMetadataVersion(), asset.getTagsJson());
    }

    public Optional<Asset> findByKey(String assetKey) {
        long tenantId = tenantAccessService.requireCurrentTenantId();
        List<Asset> assets = jdbcTemplate.query("SELECT * FROM rf_asset WHERE tenant_id = ? AND asset_key = ? AND deleted = 0",
                new BeanPropertyRowMapper<>(Asset.class), tenantId, assetKey);
        return assets.stream().findFirst();
    }

    @Transactional
    public boolean softDelete(long assetId) {
        long tenantId = tenantAccessService.requireCurrentTenantId();
        return jdbcTemplate.update("UPDATE rf_asset SET deleted = 1, status = 'DELETED', updated_at = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND tenant_id = ? AND deleted = 0", assetId, tenantId) == 1;
    }

    public IPage<Asset> page(int page, int pageSize) {
        long tenantId = tenantAccessService.requireCurrentTenantId();
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(pageSize, 1);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rf_asset WHERE tenant_id = ? AND deleted = 0", Long.class, tenantId);
        List<Asset> records = jdbcTemplate.query("SELECT * FROM rf_asset WHERE tenant_id = ? AND deleted = 0 "
                        + "ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?", new BeanPropertyRowMapper<>(Asset.class),
                tenantId, safeSize, (safePage - 1L) * safeSize);
        Page<Asset> result = new Page<>(safePage, safeSize, total);
        result.setRecords(records);
        return result;
    }
}
