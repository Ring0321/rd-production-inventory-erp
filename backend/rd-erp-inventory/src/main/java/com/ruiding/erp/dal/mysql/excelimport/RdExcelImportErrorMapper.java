package com.ruiding.erp.dal.mysql.excelimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.excelimport.vo.RdExcelImportErrorPageReqVO;
import com.ruiding.erp.dal.dataobject.excelimport.RdExcelImportErrorDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RdExcelImportErrorMapper extends BaseMapperX<RdExcelImportErrorDO> {

    default PageResult<RdExcelImportErrorDO> selectPage(RdExcelImportErrorPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdExcelImportErrorDO>()
                .eq(RdExcelImportErrorDO::getTaskId, reqVO.getTaskId())
                .orderByAsc(RdExcelImportErrorDO::getRowNo)
                .orderByAsc(RdExcelImportErrorDO::getId));
    }

}
