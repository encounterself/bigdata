package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.time.OffsetDateTime;

@Data @TableName("rf_asset_lifecycle")
public class AssetLifecycle {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long assetId; private String lifecycleStatus;
    private OffsetDateTime effectiveAt; private OffsetDateTime retireAt; private String source; private String reason; private Boolean isCurrent;
    private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
