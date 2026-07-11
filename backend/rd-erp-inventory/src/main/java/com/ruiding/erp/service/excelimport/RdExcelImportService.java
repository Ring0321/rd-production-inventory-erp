package com.ruiding.erp.service.excelimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.excelimport.vo.RdExcelImportErrorPageReqVO;
import com.ruiding.erp.controller.admin.excelimport.vo.RdExcelImportRowPageReqVO;
import com.ruiding.erp.controller.admin.excelimport.vo.RdExcelImportTaskPageReqVO;
import com.ruiding.erp.dal.dataobject.excelimport.RdExcelImportErrorDO;
import com.ruiding.erp.dal.dataobject.excelimport.RdExcelImportRowDO;
import com.ruiding.erp.dal.dataobject.excelimport.RdExcelImportTaskDO;
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
