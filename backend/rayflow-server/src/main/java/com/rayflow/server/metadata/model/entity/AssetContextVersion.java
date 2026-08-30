package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("rf_asset_context_version")
public class AssetContextVersion {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long assetId;
    private Long versionNo;
    private String contextStatus;
    private OffsetDateTime observedAt;
    private String source;
    private BigDecimal confidence;
    private String freshness;
    private String contextJson;
    private String changeSummaryJson;
    private Long collectionRunId;
    private Boolean isCurrent;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer deleted;
}
