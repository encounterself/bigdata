package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("rf_asset_quality_snapshot")
public class AssetQualitySnapshot {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long assetId; private Long contextVersionId;
    private OffsetDateTime observedAt; private String qualityStatus; private BigDecimal score; private BigDecimal completenessScore;
    private BigDecimal freshnessScore; private BigDecimal validityScore; private BigDecimal uniquenessScore; private Integer ruleCount;
    private Integer failedRuleCount; private String detailsJson; private String source; private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt; private Integer deleted;
}
