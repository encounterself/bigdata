package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.math.BigDecimal; import java.time.OffsetDateTime;

@Data @TableName("rf_asset_glossary")
public class AssetGlossary {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private Long assetId; private Long glossaryTermId;
    private String columnName; private String source; private BigDecimal confidence; private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt; private Integer deleted;
}
