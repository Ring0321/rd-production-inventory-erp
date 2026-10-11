package com.ruiding.erp.service.packageunit;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceEventRespVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdLabelPrintLogDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayScanRecordDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingScanRecordDO;
import com.ruiding.erp.dal.dataobject.loading.RdLoadingExceptionDO;
import com.ruiding.erp.dal.dataobject.putaway.RdPutawayExceptionDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper;
import org.springframework.transaction.annotation.Isolation;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionMemberDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.packageunit.RdLabelPrintLogMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageTaskEventMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayScanRecordMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingScanRecordMapper;
import com.ruiding.erp.dal.mysql.loading.RdLoadingExceptionMapper;
import com.ruiding.erp.dal.mysql.putaway.RdPutawayExceptionMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMemberMapper;
import com.ruiding.erp.dal.mysql.stock.RdStockTransactionMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdPackageUnitServiceImpl implements RdPackageUnitService {

    @Resource
    private RdPackageUnitMapper packageMapper;
    @Resource
    private RdProductionOutputMapper productionMapper;
    @Resource private RdProductionTaskMapper productionTaskMapper;
    @Resource
    private RdLabelPrintLogMapper printLogMapper;
    @Resource
    private RdDigitalLinkService digitalLinkService;
    @Resource
    private RdQualityInspectionMapper qualityInspectionMapper;
    @Resource private RdQualityInspectionMemberMapper qualityMemberMapper;
    @Resource
    private RdStockTransactionMapper stockTransactionMapper;
    @Resource private RdPackageTaskEventMapper taskEventMapper;
    @Resource private RdPutawayScanRecordMapper putawayScanRecordMapper;
    @Resource private RdLoadingScanRecordMapper loadingScanRecordMapper;
    @Resource private RdPutawayExceptionMapper putawayExceptionMapper;
    @Resource private RdLoadingExceptionMapper loadingExceptionMapper;

    private final RdPackageCodeGenerator codeGenerator = new RdPackageCodeGenerator();

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public List<RdPackageUnitDO> generate(Long productionOutputId, Integer packageCount) {
        RdProductionOutputDO before = productionMapper.selectById(productionOutputId);
        if (before == null) throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        if (before.getProductionTaskId() != null) {
            RdProductionTaskDO task = productionTaskMapper.selectByIdForUpdate(before.getProductionTaskId());
            if (task == null) throw exception(PRODUCTION_TASK_NOT_EXISTS);
            if (!"ACTIVE".equals(task.getStatus())) throw exception(PRODUCTION_TASK_NOT_ACTIVE);
        }
        RdProductionOutputDO production = productionMapper.selectByIdForUpdate(productionOutputId);
        if (production == null) {
            throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        }
        if (!RdProductionOutputStatusEnum.CONFIRMED.getStatus().equals(production.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_STATUS_INVALID);
        }
        if (!java.util.Objects.equals(before.getProductionTaskId(), production.getProductionTaskId())) {
            throw exception(PRODUCTION_OUTPUT_VERSION_CONFLICT);
        }
        if (packageMapper.selectCountByProductionOutputId(productionOutputId) > 0) {
            throw exception(PACKAGE_ALREADY_GENERATED);
        }
        if (packageCount == null || production.getOutputQty().stripTrailingZeros().scale() > 0
                || production.getOutputQty().intValueExact() != packageCount) {
            throw exception(PACKAGE_COUNT_MISMATCH);
        }
        Long tenantId = TenantContextHolder.getTenantId() == null ? 1L : TenantContextHolder.getTenantId();
        packageMapper.incrementDailySequence(tenantId, production.getProductionDate(), packageCount);
        int endSequence = packageMapper.selectCurrentDailySequenceForUpdate(tenantId, production.getProductionDate());
        int sequence = endSequence - packageCount + 1;
        if (endSequence > 9999) {
            throw exception(PACKAGE_DAILY_SEQUENCE_EXHAUSTED);
        }
        List<RdPackageUnitDO> result = new ArrayList<>(packageCount);
        for (int i = 0; i < packageCount; i++) {
            String code = codeGenerator.generate(production.getProductionDate(), sequence + i);
            RdPackageUnitDO unit = new RdPackageUnitDO()
                    .setPackageCode(code)
                    .setQrCode(digitalLinkService.build(code))
                    .setProductionOutputId(production.getId())
                    .setProductionTaskId(production.getProductionTaskId()).setProductionTaskNo(production.getProductionTaskNo())
                    .setShiftCode(production.getShiftCode()).setFurnaceOutDate(production.getFurnaceOutDate())
                    .setOperatorId(production.getOperatorId()).setOperatorName(production.getOperatorName())
                    .setProductionDate(production.getProductionDate())
                    .setCustomerName(production.getCustomerName())
                    .setProductModel(production.getProductModel())
                    .setFurnaceNo(production.getFurnaceNo())
                    .setFurnaceLayer(production.getFurnaceLayer())
                    .setPackageWeight(production.getPackageWeight())
                    .setLocationId(production.getLocationId())
                    .setLocationCode(production.getLocationCode())
                    .setStatus(RdPackageStatusEnum.CREATED.getStatus())
                    .setVersion(0);
            packageMapper.insert(unit);
            result.add(unit);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPrinted(List<Long> packageIds, Long operatorId, String operatorName,
                            boolean reprint, String reprintReason) {
        if (packageIds == null || packageIds.isEmpty() || packageIds.size() > 500
                || packageIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(packageIds).size() != packageIds.size()) {
            throw exception(LABEL_PRINT_SELECTION_INVALID);
        }
        if (reprint && (reprintReason == null || reprintReason.isBlank())) {
            throw exception(LABEL_REPRINT_REASON_REQUIRED);
        }
        if (reprintReason != null && reprintReason.length() > 500) {
            throw exception(LABEL_PRINT_SELECTION_INVALID);
        }
        List<RdPackageUnitDO> units = new ArrayList<>(packageIds.size());
        // Lock in ID order and validate the whole selection before changing any package.
        for (Long packageId : packageIds.stream().sorted().toList()) {
            RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(packageId);
            if (unit == null) {
                throw exception(PACKAGE_NOT_EXISTS);
            }
            boolean created = RdPackageStatusEnum.CREATED.getStatus().equals(unit.getStatus());
            boolean printable = java.util.Arrays.stream(RdPackageStatusEnum.values())
                    .anyMatch(status -> status.getStatus().equals(unit.getStatus())
                            && status != RdPackageStatusEnum.VOIDED);
            if (!printable || (reprint ? created : !created)) {
                throw exception(PACKAGE_STATUS_INVALID);
            }
            units.add(unit);
        }
        LocalDateTime printTime = LocalDateTime.now();
        for (RdPackageUnitDO unit : units) {
            if (!reprint) {
                unit.setStatus(RdPackageStatusEnum.LABELED.getStatus());
                packageMapper.updateById(unit);
            }
            long previous = printLogMapper.selectCount(RdLabelPrintLogDO::getPackageId, unit.getId());
            printLogMapper.insert(new RdLabelPrintLogDO().setPackageId(unit.getId()).setTemplateCode("GOODS_CARD")
                    .setPrintSequence((int) previous + 1).setReprint(reprint)
                    .setReprintReason(reprint ? reprintReason.trim() : null)
                    .setPrinterId(operatorId).setPrinterName(operatorName).setPrintTime(printTime));
        }
    }

    @Override
    public RdPackageUnitDO get(Long id) {
        RdPackageUnitDO unit = packageMapper.selectById(id);
        if (unit == null) {
            throw exception(PACKAGE_NOT_EXISTS);
        }
        return unit;
    }

    @Override
    public List<RdPackageUnitDO> getByProductionOutputId(Long productionOutputId) {
        return packageMapper.selectListByProductionOutputId(productionOutputId);
    }

    @Override
    public List<RdPackageUnitDO> getByProductionTaskId(Long taskId) {
        if (productionTaskMapper.selectById(taskId) == null) throw exception(PRODUCTION_TASK_NOT_EXISTS);
        return packageMapper.selectListByProductionTaskId(taskId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markTaskPrinted(Long taskId, List<Long> packageIds, Long operatorId, String operatorName,
                                boolean reprint, String reason) {
        if (productionTaskMapper.selectByIdForUpdate(taskId) == null) throw exception(PRODUCTION_TASK_NOT_EXISTS);
        if (packageIds == null || packageIds.isEmpty() || packageIds.size() > 500) throw exception(LABEL_PRINT_SELECTION_INVALID);
        for (Long id : packageIds) {
            RdPackageUnitDO unit = packageMapper.selectById(id);
            if (unit == null || !taskId.equals(unit.getProductionTaskId())) throw exception(PRODUCTION_TASK_PRINT_MISMATCH);
        }
        markPrinted(packageIds, operatorId, operatorName, reprint, reason);
    }

    @Override
    public List<RdPackageUnitDO> trace(String packageCode, Long productionOutputId) {
        if (packageCode != null && !packageCode.isBlank()) {
            RdPackageUnitDO unit = packageMapper.selectByPackageCode(packageCode.trim());
            return unit == null ? Collections.emptyList() : List.of(unit);
        }
        return productionOutputId == null ? Collections.emptyList()
                : packageMapper.selectListByProductionOutputId(productionOutputId);
    }

    @Override
    public RdPackageTraceRespVO getTraceDetail(String packageCode, boolean internalView) {
        if (packageCode == null || packageCode.isBlank()) {
            return null;
        }
        RdPackageUnitDO unit = packageMapper.selectByPackageCode(packageCode.trim());
        if (unit == null) {
            return null;
        }
        List<RdQualityInspectionDO> inspections = qualityInspectionMapper.selectListByCoveredPackageId(unit.getId());
        List<RdStockTransactionDO> transactions = stockTransactionMapper.selectList(
                new LambdaQueryWrapperX<RdStockTransactionDO>()
                        .eq(RdStockTransactionDO::getPackageId, unit.getId())
                        .orderByAsc(RdStockTransactionDO::getId));
        RdQualityInspectionDO latestInspection = inspections.isEmpty()
                ? null : inspections.get(inspections.size() - 1);
        List<RdPackageTraceEventRespVO> events = new ArrayList<>();
        addEvent(events, "PRODUCTION", "货物身份已生成", unit.getCreateTime(),
                internalView ? "一包一码已建立 / 生产记录 " + unit.getProductionOutputId() : "一包一码已建立");
        for (RdLabelPrintLogDO log : printLogMapper.selectList(new LambdaQueryWrapperX<RdLabelPrintLogDO>()
                .eq(RdLabelPrintLogDO::getPackageId, unit.getId()).orderByAsc(RdLabelPrintLogDO::getId))) {
            boolean reprint = Boolean.TRUE.equals(log.getReprint());
            String detail = reprint ? "原包号保持不变" : "标签打印已登记";
            if (internalView) {
                detail = appendOperator("第 " + log.getPrintSequence() + " 次打印"
                        + (reprint ? " / 原因：" + nonBlank(log.getReprintReason()) : ""), log.getPrinterName());
            }
            addEvent(events, "LABEL", reprint ? "标签补打已登记" : "标签首次打印已登记", log.getPrintTime(), detail);
        }
        for (RdQualityInspectionDO inspection : inspections) {
            addEvent(events, "QUALITY", "已提交质检", inspection.getSubmitTime(),
                    internalView ? appendOperator("等待复核", inspection.getSubmitterName()) : "等待复核");
            String qualityTitle = qualityTitle(inspection.getConclusion());
            addEvent(events, "QUALITY", qualityTitle, inspection.getReviewTime(),
                    internalView ? appendOperator(qualityTitle, inspection.getReviewerName()) : qualityTitle);
        }
        if (internalView) {
            List<RdPackageTaskEventDO> taskEvents = taskEventMapper.selectByPackageId(unit.getId());
            for (RdPackageTaskEventDO event : taskEvents) {
                String detail = nonBlank(event.getSourceNo()) + " / 版本 " + event.getSourceVersion();
                if (event.getPreviousLocationCode() != null) {
                    detail += " / 原计划库位 " + event.getPreviousLocationCode();
                }
                if (event.getLocationCode() != null) {
                    detail += " / 库位 " + event.getLocationCode();
                }
                if (event.getRelatedPackageCode() != null) {
                    detail += " / 关联包 " + event.getRelatedPackageCode();
                }
                if (event.getDetail() != null && !event.getDetail().isBlank()) {
                    detail += " / " + event.getDetail();
                }
                addEvent(events, event.getStage(), taskEventTitle(event), event.getEventTime(),
                        appendOperator(detail, event.getOperatorName()));
            }
            for (RdPutawayScanRecordDO scan : putawayScanRecordMapper.selectList(
                    new LambdaQueryWrapperX<RdPutawayScanRecordDO>().eq(RdPutawayScanRecordDO::getPackageId, unit.getId())
                            .eq(RdPutawayScanRecordDO::getSuccess, true).eq(RdPutawayScanRecordDO::getScanResult, "SUCCESS")
                            .orderByAsc(RdPutawayScanRecordDO::getId))) {
                addEvent(events, "PUTAWAY", "仓管已核验到位包件", scan.getScanTime(),
                        appendOperator("上架任务 ID " + scan.getTaskId() + " / 仅核验，尚非库存入账",
                                scan.getOperatorName()));
            }
            for (RdLoadingScanRecordDO scan : loadingScanRecordMapper.selectList(
                    new LambdaQueryWrapperX<RdLoadingScanRecordDO>().eq(RdLoadingScanRecordDO::getPackageId, unit.getId())
                            .eq(RdLoadingScanRecordDO::getSuccess, true).eq(RdLoadingScanRecordDO::getScanResult, "BATCH_CONFIRMED")
                            .orderByAsc(RdLoadingScanRecordDO::getId))) {
                boolean hasPackageSnapshot = taskEvents.stream().anyMatch(event ->
                        "LOADING_SCAN".equals(event.getSourceType()) && scan.getId().equals(event.getSourceId()));
                if (!hasPackageSnapshot && !taskEventMapper.hasSourceSnapshots("LOADING_SCAN", scan.getId())) {
                    addEvent(events, "LOADING", "仓管已登记批次核验（代表码）", scan.getScanTime(),
                            appendOperator("装车任务 ID " + scan.getTaskId() + " / 计划版本 " + scan.getPlanVersion(),
                                    scan.getOperatorName()));
                }
            }
            for (RdPutawayExceptionDO item : putawayExceptionMapper.selectList(
                    new LambdaQueryWrapperX<RdPutawayExceptionDO>().eq(RdPutawayExceptionDO::getPackageId, unit.getId())
                            .orderByAsc(RdPutawayExceptionDO::getId))) {
                if (!hasTaskEvent(taskEvents, "PUTAWAY_EXCEPTION", item.getId(), "EXCEPTION_REPORTED")) {
                    addEvent(events, "EXCEPTION", "现场异常已上报", item.getReportedTime(), appendOperator(
                            item.getExceptionNo() + " / 上架任务 ID " + item.getTaskId() + " / " + item.getDescription(),
                            item.getReporterName()));
                }
                if (!hasTaskEvent(taskEvents, "PUTAWAY_EXCEPTION", item.getId(), "EXCEPTION_RESOLVED")) {
                    addEvent(events, "EXCEPTION", "仓管已处理现场异常", item.getHandleTime(), appendOperator(
                            item.getExceptionNo() + " / " + item.getResolutionAction() + " / " + item.getHandlingResult(),
                            item.getHandlerName()));
                }
            }
            for (RdLoadingExceptionDO item : loadingExceptionMapper.selectList(
                    new LambdaQueryWrapperX<RdLoadingExceptionDO>().eq(RdLoadingExceptionDO::getPackageId, unit.getId())
                            .orderByAsc(RdLoadingExceptionDO::getId))) {
                if (!hasTaskEvent(taskEvents, "LOADING_EXCEPTION", item.getId(), "EXCEPTION_REPORTED")) {
                    addEvent(events, "EXCEPTION", "现场异常已上报", item.getReportedTime(), appendOperator(
                            item.getExceptionNo() + " / 装车任务 ID " + item.getTaskId() + " / " + item.getDescription(),
                            item.getReporterName()));
                }
                if (!hasTaskEvent(taskEvents, "LOADING_EXCEPTION", item.getId(), "EXCEPTION_RESOLVED")) {
                    addEvent(events, "EXCEPTION", "仓管已处理现场异常", item.getHandleTime(), appendOperator(
                            item.getExceptionNo() + " / " + item.getResolutionAction() + " / " + item.getHandlingResult(),
                            item.getHandlerName()));
                }
            }
        }
        for (RdStockTransactionDO transaction : transactions) {
            String title = stockTransactionTitle(transaction.getTransactionType());
            String detail = internalView
                    ? String.join(" / ", nonBlank(transaction.getSourceNo()),
                            nonBlank(transaction.getOperatorName()), nonBlank(transaction.getLocationCode()))
                    : publicStockDetail(transaction.getTransactionType());
            addEvent(events, "STOCK", title, transaction.getCreateTime(), detail);
        }
        events.sort(Comparator.comparing(RdPackageTraceEventRespVO::getEventTime));
        RdPackageTraceRespVO response = new RdPackageTraceRespVO()
                .setPackageCode(unit.getPackageCode())
                .setDigitalLink(digitalLinkService.build(unit.getPackageCode()))
                .setStatus(unit.getStatus())
                .setStatusName(packageStatusName(unit.getStatus()))
                .setProductionDate(unit.getProductionDate())
                .setFurnaceOutDate(unit.getFurnaceOutDate())
                .setShiftCode(unit.getShiftCode())
                .setProductName(unit.getProductName())
                .setProductModel(unit.getProductModel())
                .setFurnaceNo(unit.getFurnaceNo())
                .setFurnaceLayer(unit.getFurnaceLayer())
                .setPackageWeight(unit.getPackageWeight())
                .setQualityStatus(latestInspection == null ? null : latestInspection.getStatus())
                .setQualityConclusion(latestInspection == null ? null : latestInspection.getConclusion())
                .setInternalView(internalView)
                .setEvents(events);
        if (internalView) {
            response.setCustomerName(unit.getCustomerName())
                    .setProductionTaskId(unit.getProductionTaskId()).setProductionTaskNo(unit.getProductionTaskNo())
                    .setLocationCode(unit.getLocationCode())
                    .setProductionOutputId(unit.getProductionOutputId())
                    .setQualityInspectionId(latestInspection == null ? null : latestInspection.getId());
            if (latestInspection != null) {
                List<RdQualityInspectionMemberDO> members = qualityMemberMapper.selectListByInspectionId(latestInspection.getId());
                members.stream().filter(member -> unit.getId().equals(member.getPackageId())).findFirst()
                        .ifPresent(member -> response.setQualityCoverageCount(members.size())
                                .setQualitySampleCount((int) members.stream().filter(value -> Boolean.TRUE.equals(value.getSampled())).count())
                                .setQualitySampled(member.getSampled()));
            }
        }
        return response;
    }

    private static void addEvent(List<RdPackageTraceEventRespVO> events, String stage, String title,
                                 LocalDateTime eventTime, String detail) {
        if (eventTime == null) {
            return;
        }
        events.add(new RdPackageTraceEventRespVO().setStage(stage).setTitle(title)
                .setEventTime(eventTime).setDetail(detail));
    }

    private static String appendOperator(String detail, String operatorName) {
        return operatorName == null || operatorName.isBlank() ? detail : detail + " / " + operatorName;
    }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String qualityTitle(String conclusion) {
        if ("PASS".equalsIgnoreCase(conclusion) || "PASSED".equalsIgnoreCase(conclusion)) {
            return "质检通过";
        }
        if ("REJECT".equalsIgnoreCase(conclusion) || "REJECTED".equalsIgnoreCase(conclusion)) {
            return "质检未通过";
        }
        return "质检已复核";
    }

    private static boolean hasTaskEvent(List<RdPackageTaskEventDO> events, String sourceType,
                                         Long sourceId, String eventCode) {
        return events.stream().anyMatch(event -> sourceType.equals(event.getSourceType())
                && sourceId.equals(event.getSourceId()) && eventCode.equals(event.getEventCode()));
    }

    private static String taskEventTitle(RdPackageTaskEventDO event) {
        return switch (event.getEventCode()) {
            case "ASSIGNED" -> "PUTAWAY".equals(event.getStage()) ? "已派上架任务" : "已派装车任务";
            case "REASSIGNED" -> "上架任务已改派";
            case "RETURNED" -> "装车任务已恢复执行";
            case "CANCELLED" -> "装车任务已取消";
            case "REPLACED_OUT" -> "包件已从装车任务换出";
            case "REPLACED_IN" -> "包件作为替代货加入装车任务";
            case "SCAN_CONFIRMED" -> "仓管已核验装车包件";
            case "EXCEPTION_REPORTED" -> "现场异常已上报";
            case "EXCEPTION_RESOLVED" -> "仓管已处理现场异常";
            default -> "任务状态已记录";
        };
    }

    private static String stockTransactionTitle(String type) {
        if (type == null) {
            return "库存状态已更新";
        }
        return switch (type) {
            case "OPENING", "PRODUCTION_IN", "INBOUND", "RETURN_IN" -> "已完成入库";
            case "OUTBOUND" -> "已完成出库";
            case "TRANSFER_IN", "TRANSFER_OUT" -> "库存归属已调整";
            case "ADJUST_IN", "ADJUST_OUT" -> "库存已盘点调整";
            default -> "库存状态已更新";
        };
    }

    private static String publicStockDetail(String type) {
        return "OUTBOUND".equals(type) ? "货物已完成出库" : "库存状态已更新";
    }

    private static String packageStatusName(String status) {
        if (status == null) {
            return "未知状态";
        }
        return switch (status) {
            case "CREATED" -> "待贴标";
            case "LABELED" -> "已贴标";
            case "QC_PENDING" -> "待质检";
            case "QC_PASSED" -> "质检通过";
            case "QC_REJECTED" -> "质检未通过";
            case "INBOUND_PENDING" -> "待上架";
            case "IN_STOCK" -> "在库";
            case "STOCKTAKE_MISSING" -> "盘点异常";
            case "OUTBOUND_PENDING" -> "装车中";
            case "OUTBOUNDED" -> "已出库";
            case "VOIDED" -> "已作废";
            default -> status;
        };
    }

    @Override
    public PageResult<RdPackageUnitDO> page(PageParam pageParam) {
        return packageMapper.selectPage(pageParam, new LambdaQueryWrapperX<RdPackageUnitDO>()
                .orderByDesc(RdPackageUnitDO::getId));
    }
}
