package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.time.OffsetDateTime;

@Data @TableName("rf_asset_owner")
public class AssetOwner {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private String ownerType; private String ownerKey;
    private String displayName; private String email; private String source; private Boolean isPrimary; private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt; private Integer deleted;
}
