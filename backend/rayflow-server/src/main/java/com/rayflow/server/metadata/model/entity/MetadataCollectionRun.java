package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.time.OffsetDateTime;

@Data @TableName("rf_metadata_collection_run")
public class MetadataCollectionRun {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private String runKey; private String runType; private String status;
    private Long requestedBy; private String platform; private Long connectionId; private Long assetId; private OffsetDateTime requestedAt;
    private OffsetDateTime startedAt; private OffsetDateTime finishedAt; private Integer attempt; private Integer maxAttempts;
    private Integer totalItems; private Integer successItems; private Integer failedItems; private String errorSummary; private String parametersJson;
    private String resultJson; private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
