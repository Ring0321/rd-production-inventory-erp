package cn.iocoder.yudao.module.rd.service.excelimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportErrorPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportRowPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportTaskPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportErrorDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportRowDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportTaskDO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface RdExcelImportService {

    Long precheck(MultipartFile file, LocalDate bizDate, String sheetName);

    Integer confirm(Long taskId, Long operatorId, String operatorName);

    RdExcelImportTaskDO getTask(Long id);

    PageResult<RdExcelImportTaskDO> getTaskPage(RdExcelImportTaskPageReqVO reqVO);

    PageResult<RdExcelImportRowDO> getRowPage(RdExcelImportRowPageReqVO reqVO);

    PageResult<RdExcelImportErrorDO> getErrorPage(RdExcelImportErrorPageReqVO reqVO);

}
