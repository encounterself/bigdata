package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.time.OffsetDateTime;

@Data @TableName("rf_metadata_event_delivery")
public class MetadataEventDelivery {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long metadataEventId; private String consumerName; private String status;
    private Integer attempt; private OffsetDateTime lastAttemptAt; private String errorMessage; private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
