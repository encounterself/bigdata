package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("rf_asset_column")
public class AssetColumn {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private Long assetId;
    private Long contextVersionId;
    private Integer ordinalPosition;
    private String columnName;
    private String dataType;
    private String sourceDataType;
    private Boolean nullable;
    private String defaultExpression;
    private String comment;
    private Boolean isPrimaryKey;
    private Boolean isPartitionKey;
    private Boolean isBucketKey;
    private String classification;
    private String columnStatsJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer deleted;
}
