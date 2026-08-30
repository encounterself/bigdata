package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.time.OffsetDateTime;

@Data @TableName("rf_metadata_event")
public class MetadataEvent {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private String eventId; private Integer version; private String eventType;
    private String source; private OffsetDateTime occurredAt; private OffsetDateTime receivedAt; private String assetKey; private String processingStatus;
    private String correlationId; private String payloadJson; private String errorMessage; private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
