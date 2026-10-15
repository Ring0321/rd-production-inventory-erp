package com.ruiding.erp.dal.mysql.visual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.visual.RdVisualDocumentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdVisualDocumentMapper extends BaseMapperX<RdVisualDocumentDO> {
    @Select("SELECT * FROM rd_visual_document WHERE CAST(kind AS BINARY)=CAST(#{kind} AS BINARY) AND CAST(scope_id AS BINARY)=CAST(#{scopeId} AS BINARY) AND deleted=0 FOR UPDATE")
    RdVisualDocumentDO lock(@Param("kind") String kind, @Param("scopeId") String scopeId);
    @Select("SELECT * FROM rd_visual_document WHERE CAST(kind AS BINARY)=CAST(#{kind} AS BINARY) AND CAST(scope_id AS BINARY)=CAST(#{scopeId} AS BINARY) AND deleted=0")
    RdVisualDocumentDO getExact(@Param("kind") String kind, @Param("scopeId") String scopeId);
}
