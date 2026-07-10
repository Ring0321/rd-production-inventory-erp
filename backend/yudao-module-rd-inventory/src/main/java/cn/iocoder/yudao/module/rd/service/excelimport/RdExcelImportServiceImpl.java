package cn.iocoder.yudao.module.rd.service.excelimport;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportErrorPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportRowPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo.RdExcelImportTaskPageReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportErrorDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportRowDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportTaskDO;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportErrorMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportRowMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.EXCEL_IMPORT_FILE_INVALID;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.EXCEL_IMPORT_PARSE_FAILED;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.EXCEL_IMPORT_TASK_NOT_EXISTS;

@Service
@Validated
public class RdExcelImportServiceImpl implements RdExcelImportService {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    @Resource
    private RdInventoryExcelParser excelParser;
    @Resource
    private RdExcelImportTransactionService transactionService;
    @Resource
    private RdExcelImportTaskMapper taskMapper;
    @Resource
    private RdExcelImportRowMapper rowMapper;
    @Resource
    private RdExcelImportErrorMapper errorMapper;

    @Override
    public Long precheck(MultipartFile file, LocalDate bizDate, String sheetName) {
        validateFile(file);
        String targetSheetName = StringUtils.hasText(sheetName)
                ? sheetName.trim() : RdInventoryExcelParser.DEFAULT_SHEET_NAME;
        try {
            RdInventoryExcelParser.ParseResult result = excelParser.parse(file.getInputStream(), targetSheetName);
            return transactionService.savePrecheck(
                    StringUtils.cleanPath(file.getOriginalFilename()), targetSheetName, bizDate, result);
        } catch (IOException exception) {
            throw exception(EXCEL_IMPORT_PARSE_FAILED, exception.getMessage());
        }
    }

    @Override
    public Integer confirm(Long taskId, Long operatorId, String operatorName) {
        return transactionService.confirm(taskId, operatorId, operatorName);
    }

    @Override
    public RdExcelImportTaskDO getTask(Long id) {
        RdExcelImportTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw exception(EXCEL_IMPORT_TASK_NOT_EXISTS);
        }
        return task;
    }

    @Override
    public PageResult<RdExcelImportTaskDO> getTaskPage(RdExcelImportTaskPageReqVO reqVO) {
        return taskMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<RdExcelImportRowDO> getRowPage(RdExcelImportRowPageReqVO reqVO) {
        return rowMapper.selectPage(reqVO);
    }

    @Override
    public PageResult<RdExcelImportErrorDO> getErrorPage(RdExcelImportErrorPageReqVO reqVO) {
        return errorMapper.selectPage(reqVO);
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE
                || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw exception(EXCEL_IMPORT_FILE_INVALID);
        }
    }

}
