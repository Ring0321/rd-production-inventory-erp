package com.ruiding.erp.service.packageunit;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceEventRespVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdLabelPrintLogDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.dal.dataobject.stock.RdStockTransactionDO;
import com.ruiding.erp.dal.mysql.packageunit.RdLabelPrintLogMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.quality.RdQualityInspectionMapper;
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
    @Resource
    private RdLabelPrintLogMapper printLogMapper;
    @Resource
    private RdDigitalLinkService digitalLinkService;
    @Resource
    private RdQualityInspectionMapper qualityInspectionMapper;
    @Resource
    private RdStockTransactionMapper stockTransactionMapper;

    private final RdPackageCodeGenerator codeGenerator = new RdPackageCodeGenerator();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RdPackageUnitDO> generate(Long productionOutputId, Integer packageCount) {
        RdProductionOutputDO production = productionMapper.selectByIdForUpdate(productionOutputId);
        if (production == null) {
            throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        }
        if (!RdProductionOutputStatusEnum.CONFIRMED.getStatus().equals(production.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_STATUS_INVALID);
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
        List<RdQualityInspectionDO> inspections = qualityInspectionMapper.selectList(
                new LambdaQueryWrapperX<RdQualityInspectionDO>()
                        .eq(RdQualityInspectionDO::getPackageId, unit.getId())
                        .orderByAsc(RdQualityInspectionDO::getId));
        List<RdStockTransactionDO> transactions = stockTransactionMapper.selectList(
                new LambdaQueryWrapperX<RdStockTransactionDO>()
                        .eq(RdStockTransactionDO::getPackageId, unit.getId())
                        .orderByAsc(RdStockTransactionDO::getId));
        RdQualityInspectionDO latestInspection = inspections.isEmpty()
                ? null : inspections.get(inspections.size() - 1);
        List<RdPackageTraceEventRespVO> events = new ArrayList<>();
        addEvent(events, "PRODUCTION", "货物身份已生成", unit.getCreateTime(), "一包一码已建立");
        for (RdQualityInspectionDO inspection : inspections) {
            addEvent(events, "QUALITY", "已提交质检", inspection.getSubmitTime(),
                    internalView ? appendOperator("等待复核", inspection.getSubmitterName()) : "等待复核");
            String qualityTitle = "PASS".equalsIgnoreCase(inspection.getConclusion())
                    ? "质检通过" : "REJECT".equalsIgnoreCase(inspection.getConclusion())
                    ? "质检未通过" : "质检已复核";
            addEvent(events, "QUALITY", qualityTitle, inspection.getReviewTime(),
                    internalView ? appendOperator(inspection.getConclusion(), inspection.getReviewerName())
                            : inspection.getConclusion());
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
                    .setLocationCode(unit.getLocationCode())
                    .setProductionOutputId(unit.getProductionOutputId())
                    .setQualityInspectionId(latestInspection == null ? null : latestInspection.getId());
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
