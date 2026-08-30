package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("rf_asset_statistics")
public class AssetStatistics {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId; private Long assetId; private Long contextVersionId;
    private OffsetDateTime observedAt; private BigDecimal rowCount; private BigDecimal dataSizeBytes;
    private Long fileCount; private String snapshotId; private Long partitionCount; private BigDecimal nullCount;
    private BigDecimal nullRatio; private BigDecimal distinctCount; private OffsetDateTime freshnessAt;
    private String statsJson; private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
