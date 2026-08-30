package com.rayflow.server.metadata.model.entity;

import com.baomidou.mybatisplus.annotation.IdType; import com.baomidou.mybatisplus.annotation.TableId; import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data; import java.time.OffsetDateTime;

@Data @TableName("rf_glossary_term")
public class GlossaryTerm {
    @TableId(type = IdType.AUTO) private Long id; private Long tenantId; private String term; private String definition;
    private String synonymsJson; private Long ownerId; private String status; private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt; private Integer deleted;
}
