package cn.iocoder.yudao.module.rd.dal.mysql.excelimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportTaskPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RdExcelImportTaskMapper extends BaseMapperX<RdExcelImportTaskDO> {

    @Select("SELECT * FROM rd_excel_import_task WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdExcelImportTaskDO selectByIdForUpdate(Long id);

    default PageResult<RdExcelImportTaskDO> selectPage(RdExcelImportTaskPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdExcelImportTaskDO>()
                .likeIfPresent(RdExcelImportTaskDO::getFileName, reqVO.getFileName())
                .eqIfPresent(RdExcelImportTaskDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RdExcelImportTaskDO::getBizDate, reqVO.getBizDate())
                .orderByDesc(RdExcelImportTaskDO::getId));
    }

}
