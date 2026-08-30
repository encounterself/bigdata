package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("rf_asset")
public class Asset {
    @TableId(type = IdType.AUTO) private Long id;
    private Long tenantId;
    private String assetKey;
    private String platform;
    private Long connectionId;
    private String namespace;
    private String name;
    private String assetType;
    private String status;
    private String description;
    private String sourceLocator;
    private Long ownerId;
    private OffsetDateTime lastObservedAt;
    private Long lastCollectionRunId;
    private Integer schemaVersion;
    private Long metadataVersion;
    private String tagsJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Integer deleted;
}
