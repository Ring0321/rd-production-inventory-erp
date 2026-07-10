package cn.iocoder.yudao.module.rd.dal.mysql.excelimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportErrorPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportErrorDO;
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
