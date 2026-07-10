package cn.iocoder.yudao.module.rd.dal.mysql.excelimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportRowPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportRowDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RdExcelImportRowMapper extends BaseMapperX<RdExcelImportRowDO> {

    default PageResult<RdExcelImportRowDO> selectPage(RdExcelImportRowPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdExcelImportRowDO>()
                .eq(RdExcelImportRowDO::getTaskId, reqVO.getTaskId())
                .eqIfPresent(RdExcelImportRowDO::getStatus, reqVO.getStatus())
                .orderByAsc(RdExcelImportRowDO::getRowNo));
    }

    default List<RdExcelImportRowDO> selectValidRows(Long taskId) {
        return selectList(new LambdaQueryWrapperX<RdExcelImportRowDO>()
                .eq(RdExcelImportRowDO::getTaskId, taskId)
                .eq(RdExcelImportRowDO::getStatus, "VALID")
                .orderByAsc(RdExcelImportRowDO::getRowNo));
    }

}
