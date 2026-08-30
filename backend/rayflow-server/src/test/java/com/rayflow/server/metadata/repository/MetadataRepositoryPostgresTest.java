package com.rayflow.server.metadata.repository;

import com.rayflow.server.metadata.model.entity.Asset;
import com.rayflow.server.metadata.model.entity.AssetContextVersion;
import com.rayflow.server.service.TenantAccessService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetadataRepositoryPostgresTest {
    private static final String SCHEMA = "pr02_test";
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TenantAccessService tenantAccess;
    private static MetadataAssetRepository assets;
    private static MetadataContextRepository contexts;
    private static TransactionTemplate transactionTemplate;
    private static long tenantA = 1001;
    private static long tenantB = 1002;

    @BeforeAll
    static void setUp() throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:5432/rayflow?currentSchema=" + SCHEMA;
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "rayflow", "rayflow123");
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
            statement.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA + ".rf_tenant (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA + ".rf_user (id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA + ".rf_flink_job (id BIGINT PRIMARY KEY)");
            String migration = new String(new org.springframework.core.io.ClassPathResource("db/migration/V0.0.9__metadata_plane.sql").getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            statement.execute(migration);
            statement.execute("INSERT INTO " + SCHEMA + ".rf_tenant VALUES (" + tenantA + "), (" + tenantB + ") ON CONFLICT DO NOTHING");
        }
        tenantAccess = mock(TenantAccessService.class);
        when(tenantAccess.requireCurrentTenantId()).thenReturn(tenantA);
        assets = new MetadataAssetRepository(jdbc, tenantAccess);
        contexts = new MetadataContextRepository(jdbc, tenantAccess);
    }

    @AfterAll
    static void tearDown() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void duplicateUpsertIsIdempotentAndBumpsMetadataVersionOnUpdate() {
        Asset first = assets.upsert(asset("tenant-1001:paimon:catalog.table", "table-a"));
        Asset second = assets.upsert(asset("tenant-1001:paimon:catalog.table", "table-a-renamed"));
        assertEquals(first.getId(), second.getId());
        assertEquals(1L, second.getMetadataVersion());
        assertEquals("table-a-renamed", assets.findByKey(first.getAssetKey()).orElseThrow().getName());
    }

    @Test
    void softDeleteAllowsReinsertAndRemainsTenantScoped() {
        Asset original = assets.upsert(asset("tenant-1001:paimon:catalog.deleted-table", "deleted"));
        assertTrue(assets.softDelete(original.getId()));
        Asset replacement = assets.upsert(asset("tenant-1001:paimon:catalog.deleted-table", "replacement"));
        assertNotEquals(original.getId(), replacement.getId());
        when(tenantAccess.requireCurrentTenantId()).thenReturn(tenantB);
        assertTrue(assets.findByKey(original.getAssetKey()).isEmpty());
        assertFalse(assets.softDelete(replacement.getId()));
        when(tenantAccess.requireCurrentTenantId()).thenReturn(tenantA);
    }

    @Test
    void contextSwitchIncrementsVersionAndFlipsPreviousCurrent() {
        Asset asset = assets.upsert(asset("tenant-1001:paimon:catalog.context-table", "context"));
        AssetContextVersion first = contexts.insertCurrent(context(asset.getId(), "first"), 0);
        AssetContextVersion second = contexts.insertCurrent(context(asset.getId(), "second"), 1);
        assertEquals(1L, first.getVersionNo());
        assertEquals(2L, second.getVersionNo());
        assertEquals(second.getId(), contexts.current(asset.getId()).getId());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM " + SCHEMA + ".rf_asset_context_version WHERE asset_id = ? AND is_current", Long.class, asset.getId()));
    }

    @Test
    void concurrentWritersWithSameExpectedVersionHaveOneConflictAndNoBrokenChain() throws Exception {
        Asset asset = assets.upsert(asset("tenant-1001:paimon:catalog.concurrent-table", "concurrent"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> writers = List.of(
                    () -> insertContext(asset.getId(), "writer-a"),
                    () -> insertContext(asset.getId(), "writer-b"));
            List<Future<Boolean>> results = executor.invokeAll(writers);
            assertEquals(1, results.stream().filter(this::successful).count());
            assertEquals(1, results.stream().filter(f -> failedWithConflict(f)).count());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM " + SCHEMA + ".rf_asset_context_version WHERE asset_id = ? AND is_current AND deleted = 0", Long.class, asset.getId()));
            assertEquals(1L, jdbc.queryForObject("SELECT metadata_version FROM " + SCHEMA + ".rf_asset WHERE id = ?", Long.class, asset.getId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void paginationOnlyReturnsCurrentTenantAssets() {
        assets.upsert(asset("tenant-1001:paimon:catalog.page-table", "page"));
        when(tenantAccess.requireCurrentTenantId()).thenReturn(tenantB);
        assets.upsert(asset("tenant-1002:paimon:catalog.page-table", "other"));
        assertEquals(1, assets.page(1, 100).getRecords().size());
        when(tenantAccess.requireCurrentTenantId()).thenReturn(tenantA);
    }

    private boolean successful(Future<Boolean> future) {
        try { return future.get(); } catch (Exception ignored) { return false; }
    }

    private boolean failedWithConflict(Future<Boolean> future) {
        try { future.get(); return false; } catch (Exception exception) { return exception.getCause() instanceof MetadataConflictException; }
    }

    private boolean insertContext(long assetId, String source) {
        transactionTemplate.executeWithoutResult(status -> contexts.insertCurrent(context(assetId, source), 0));
        return true;
    }

    private Asset asset(String key, String name) {
        Asset asset = new Asset(); asset.setAssetKey(key); asset.setPlatform("PAIMON"); asset.setNamespace("catalog");
        asset.setName(name); asset.setAssetType("TABLE"); asset.setStatus("ACTIVE"); asset.setSchemaVersion(0); asset.setMetadataVersion(0L);
        return asset;
    }

    private AssetContextVersion context(long assetId, String source) {
        AssetContextVersion context = new AssetContextVersion(); context.setAssetId(assetId); context.setContextStatus("COMPLETE");
        context.setObservedAt(OffsetDateTime.now()); context.setSource(source); context.setConfidence(java.math.BigDecimal.ONE);
        context.setFreshness("FRESH"); context.setContextJson("{}"); return context;
    }
}
