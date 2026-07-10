package cn.iocoder.yudao.module.rd.service.excelimport;

import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockOpeningCreateReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportErrorDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportRowDO;
import cn.iocoder.yudao.module.rd.dal.dataobject.excelimport.RdExcelImportTaskDO;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportErrorMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportRowMapper;
import cn.iocoder.yudao.module.rd.dal.mysql.excelimport.RdExcelImportTaskMapper;
import cn.iocoder.yudao.module.rd.enums.RdExcelImportTaskStatusEnum;
import cn.iocoder.yudao.module.rd.service.stock.RdStockLotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.EXCEL_IMPORT_TASK_HAS_ERRORS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.EXCEL_IMPORT_TASK_NOT_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.EXCEL_IMPORT_TASK_NO_VALID_ROWS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.EXCEL_IMPORT_TASK_STATUS_INVALID;

@Service
public class RdExcelImportTransactionService {

    private static final int INSERT_BATCH_SIZE = 500;

    @Resource
    private RdExcelImportTaskMapper taskMapper;
    @Resource
    private RdExcelImportRowMapper rowMapper;
    @Resource
    private RdExcelImportErrorMapper errorMapper;
    @Resource
    private RdStockLotService stockLotService;

    @Transactional(rollbackFor = Exception.class)
    public Long savePrecheck(String fileName, String sheetName, LocalDate bizDate,
                             RdInventoryExcelParser.ParseResult result) {
        RdExcelImportTaskDO task = new RdExcelImportTaskDO()
                .setFileName(fileName)
                .setSheetName(sheetName)
                .setBizDate(bizDate)
                .setStatus(RdExcelImportTaskStatusEnum.PRECHECKED.getStatus())
                .setTotalRows(result.getTotalRows())
                .setValidRows(result.getValidRows())
                .setFailedRows(result.getFailedRows())
                .setSkippedRows(result.getSkippedRows())
                .setImportedRows(0)
                .setRemark(result.getFailedRows() == 0
                        ? "预检完成，可以确认导入" : "预检发现 " + result.getFailedRows() + " 条错误数据");
        taskMapper.insert(task);

        List<RdExcelImportRowDO> rows = new ArrayList<>(result.getRows().size());
        List<RdExcelImportErrorDO> errors = new ArrayList<>();
        for (RdInventoryExcelParser.ParsedRow parsedRow : result.getRows()) {
            rows.add(toImportRow(task.getId(), parsedRow));
            for (RdInventoryExcelParser.FieldError fieldError : parsedRow.getErrors()) {
                errors.add(new RdExcelImportErrorDO()
                        .setTaskId(task.getId())
                        .setRowNo(parsedRow.getRowNo())
                        .setFieldName(fieldError.getFieldName())
                        .setRawValue(abbreviate(fieldError.getRawValue(), 512))
                        .setErrorMessage(abbreviate(fieldError.getErrorMessage(), 512)));
            }
        }
        if (!rows.isEmpty()) {
            rowMapper.insertBatch(rows, INSERT_BATCH_SIZE);
        }
        if (!errors.isEmpty()) {
            errorMapper.insertBatch(errors, INSERT_BATCH_SIZE);
        }
        return task.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Integer confirm(Long taskId, Long operatorId, String operatorName) {
        RdExcelImportTaskDO task = taskMapper.selectByIdForUpdate(taskId);
        if (task == null) {
            throw exception(EXCEL_IMPORT_TASK_NOT_EXISTS);
        }
        if (RdExcelImportTaskStatusEnum.SUCCESS.getStatus().equals(task.getStatus())) {
            return task.getImportedRows();
        }
        if (!RdExcelImportTaskStatusEnum.PRECHECKED.getStatus().equals(task.getStatus())) {
            throw exception(EXCEL_IMPORT_TASK_STATUS_INVALID);
        }
        if (task.getFailedRows() != null && task.getFailedRows() > 0) {
            throw exception(EXCEL_IMPORT_TASK_HAS_ERRORS);
        }
        if (task.getValidRows() == null || task.getValidRows() == 0) {
            throw exception(EXCEL_IMPORT_TASK_NO_VALID_ROWS);
        }

        task.setStatus(RdExcelImportTaskStatusEnum.CONFIRMING.getStatus());
        taskMapper.updateById(task);
        List<RdExcelImportRowDO> rows = rowMapper.selectValidRows(taskId);
        if (rows.isEmpty()) {
            throw exception(EXCEL_IMPORT_TASK_NO_VALID_ROWS);
        }

        int importedRows = 0;
        for (RdExcelImportRowDO row : rows) {
            Long stockLotId = stockLotService.createImportedOpeningStock(
                    toOpeningRequest(task, row), taskId, row.getRowNo(), operatorId, operatorName);
            row.setStockLotId(stockLotId).setStatus("IMPORTED");
            rowMapper.updateById(row);
            importedRows++;
        }
        task.setStatus(RdExcelImportTaskStatusEnum.SUCCESS.getStatus())
                .setImportedRows(importedRows)
                .setConfirmTime(LocalDateTime.now())
                .setRemark("已确认导入 " + importedRows + " 条当前库存");
        taskMapper.updateById(task);
        return importedRows;
    }

    private static RdExcelImportRowDO toImportRow(Long taskId, RdInventoryExcelParser.ParsedRow row) {
        return new RdExcelImportRowDO()
                .setTaskId(taskId)
                .setRowNo(row.getRowNo())
                .setLocationCode(row.getLocationCode())
                .setCustomerName(row.getCustomerName())
                .setProductModel(row.getProductModel())
                .setFurnaceNo(row.getFurnaceNo())
                .setFurnaceLayer(row.getFurnaceLayer())
                .setSourceOpeningQty(row.getSourceOpeningQty())
                .setWarehouseQty(row.getWarehouseQty())
                .setPackageWeight(row.getPackageWeight())
                .setStockWeight(row.getStockWeight())
                .setStatus(row.getStatus())
                .setErrorMessage(abbreviate(row.getErrorMessage(), 512));
    }

    private static RdStockOpeningCreateReqVO toOpeningRequest(RdExcelImportTaskDO task,
                                                              RdExcelImportRowDO row) {
        RdStockOpeningCreateReqVO request = new RdStockOpeningCreateReqVO();
        request.setBizDate(task.getBizDate());
        request.setLocationCode(row.getLocationCode());
        request.setCustomerName(row.getCustomerName());
        request.setProductModel(row.getProductModel());
        request.setFurnaceNo(row.getFurnaceNo());
        request.setFurnaceLayer(row.getFurnaceLayer());
        request.setQuantity(row.getWarehouseQty());
        request.setPackageWeight(row.getPackageWeight());
        request.setRemark("历史库存 Excel 导入，任务 #" + task.getId() + "，源行 " + row.getRowNo());
        return request;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
