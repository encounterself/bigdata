package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.time.OffsetDateTime;

@Data @TableName("rf_metadata_collection_item")
public class MetadataCollectionItem {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long runId; private Long assetId; private String targetKey;
    private String status; private Integer attempt; private OffsetDateTime startedAt; private OffsetDateTime finishedAt; private OffsetDateTime observedAt;
    private String errorCode; private String errorMessage; private String resultJson; private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
