package com.ruiding.erp.dal.dataobject.visual;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data @EqualsAndHashCode(callSuper = true) @TableName("rd_visual_document_event")
public class RdVisualDocumentEventDO extends TenantBaseDO {
    @TableId private Long id;
    private Long documentId;
    private Integer version;
    private String action;
    private String content;
}
