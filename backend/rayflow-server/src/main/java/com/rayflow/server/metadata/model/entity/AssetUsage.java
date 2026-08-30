package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("rf_asset_usage")
public class AssetUsage {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long assetId; private String consumerType;
    private String consumerKey; private String accessType; private OffsetDateTime firstSeenAt; private OffsetDateTime lastSeenAt;
    private Long accessCount; private String source; private String detailsJson; private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt; private Integer deleted;
}
