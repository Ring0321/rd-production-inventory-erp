package com.ruiding.erp.dal.dataobject.visual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true) @TableName("rd_visual_document")
public class RdVisualDocumentDO extends TenantBaseDO {
    @TableId private Long id;
    private String kind;
    private String scopeId;
    private Integer version;
    private String draftContent;
    private String publishedContent;
    private Integer publishedVersion;
}
