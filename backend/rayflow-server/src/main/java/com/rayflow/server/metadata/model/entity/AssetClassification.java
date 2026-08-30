package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.math.BigDecimal; import java.time.OffsetDateTime;

@Data @TableName("rf_asset_classification")
public class AssetClassification {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long assetId; private String classification;
    private String source; private BigDecimal confidence; private String reason; private OffsetDateTime observedAt;
    private OffsetDateTime createdAt; private OffsetDateTime updatedAt; private Integer deleted;
}
