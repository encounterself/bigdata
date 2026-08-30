package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("rf_asset_lineage_edge")
public class AssetLineageEdge {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long upstreamAssetId; private Long downstreamAssetId;
    private String edgeType; private String columnMappingJson; private Long jobId; private String jobRunKey; private String source;
    private String sourceEventId; private OffsetDateTime eventTime; private String parseStatus; private String resolutionStatus;
    private OffsetDateTime observedAt; private BigDecimal confidence; private String status; private String detailsJson;
    private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
