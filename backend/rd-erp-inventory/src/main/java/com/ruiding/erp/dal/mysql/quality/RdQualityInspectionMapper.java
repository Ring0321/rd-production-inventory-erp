package com.ruiding.erp.dal.mysql.quality;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Options;
import java.util.List;
@Mapper
public interface RdQualityInspectionMapper extends BaseMapperX<RdQualityInspectionDO> {
    @Select("SELECT * FROM rd_quality_inspection WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    RdQualityInspectionDO selectByIdForUpdate(Long id);

    // READ_COMMITTED alone does not invalidate MyBatis's cached first request miss.
    @Select("SELECT * FROM rd_quality_inspection WHERE client_request_id = #{clientRequestId} AND deleted = 0")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    RdQualityInspectionDO selectByClientRequestId(String clientRequestId);

    // Legacy representative fallback applies only when no real member records exist.
    @Select("SELECT q.* FROM rd_quality_inspection q WHERE q.deleted = 0 AND ("
            + "EXISTS (SELECT 1 FROM rd_quality_inspection_member m WHERE m.tenant_id = q.tenant_id "
            + "AND m.inspection_id = q.id AND m.package_id = #{packageId} AND m.deleted = 0) OR "
            + "(q.package_id = #{packageId} AND NOT EXISTS (SELECT 1 FROM rd_quality_inspection_member m "
            + "WHERE m.tenant_id = q.tenant_id AND m.inspection_id = q.id AND m.deleted = 0))) ORDER BY q.id")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<RdQualityInspectionDO> selectListByCoveredPackageId(Long packageId);

    @Select("<script>SELECT q.* FROM rd_quality_inspection q WHERE q.deleted = 0 "
            + "AND q.status IN ('DRAFT','CORRECTED','SUBMITTED','RESUBMITTED') "
            + "<if test='excludedInspectionId != null'>AND q.id != #{excludedInspectionId} </if>AND ("
            + "EXISTS (SELECT 1 FROM rd_quality_inspection_member m WHERE m.tenant_id = q.tenant_id "
            + "AND m.inspection_id = q.id AND m.active_key = #{packageId} AND m.deleted = 0) OR "
            + "(q.package_id = #{packageId} AND NOT EXISTS (SELECT 1 FROM rd_quality_inspection_member m "
            + "WHERE m.tenant_id = q.tenant_id AND m.inspection_id = q.id AND m.deleted = 0))) "
            + "ORDER BY q.id DESC LIMIT 1</script>")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    RdQualityInspectionDO selectActiveByPackageId(@Param("packageId") Long packageId,
                                                 @Param("excludedInspectionId") Long excludedInspectionId);
}
