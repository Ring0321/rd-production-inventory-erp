package com.ruiding.erp.service.packageunit;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.dal.dataobject.packageunit.RdLabelPrintLogDO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.mysql.packageunit.RdLabelPrintLogMapper;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
public class RdPackageUnitServiceImpl implements RdPackageUnitService {
    @Resource private RdPackageUnitMapper packageMapper;
    @Resource private RdProductionOutputMapper productionMapper;
    @Resource private RdLabelPrintLogMapper printLogMapper;
    private final RdPackageCodeGenerator codeGenerator = new RdPackageCodeGenerator();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RdPackageUnitDO> generate(Long productionOutputId, Integer packageCount) {
        RdProductionOutputDO production = productionMapper.selectByIdForUpdate(productionOutputId);
        if (production == null) throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
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
        if (endSequence > 999) throw exception(PACKAGE_DAILY_SEQUENCE_EXHAUSTED);
        List<RdPackageUnitDO> result = new ArrayList<>(packageCount);
        for (int i = 0; i < packageCount; i++) {
            String code = codeGenerator.generate(production.getProductionDate(), sequence + i);
            RdPackageUnitDO unit = new RdPackageUnitDO()
                    .setPackageCode(code).setQrCode(code).setProductionOutputId(production.getId())
                    .setProductionDate(production.getProductionDate()).setCustomerName(production.getCustomerName())
                    .setProductModel(production.getProductModel()).setFurnaceNo(production.getFurnaceNo())
                    .setFurnaceLayer(production.getFurnaceLayer()).setPackageWeight(production.getPackageWeight())
                    .setLocationId(production.getLocationId()).setLocationCode(production.getLocationCode())
                    .setStatus(RdPackageStatusEnum.CREATED.getStatus()).setVersion(0);
            packageMapper.insert(unit);
            result.add(unit);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markPrinted(Long packageId, Long operatorId, String operatorName, String reprintReason) {
        RdPackageUnitDO unit = packageMapper.selectByIdForUpdate(packageId);
        if (unit == null) throw exception(PACKAGE_NOT_EXISTS);
        boolean firstPrint = RdPackageStatusEnum.CREATED.getStatus().equals(unit.getStatus());
        if (!firstPrint && !RdPackageStatusEnum.LABELED.getStatus().equals(unit.getStatus())) {
            throw exception(PACKAGE_STATUS_INVALID);
        }
        if (firstPrint) {
            unit.setStatus(RdPackageStatusEnum.LABELED.getStatus());
            packageMapper.updateById(unit);
        } else if (reprintReason == null || reprintReason.isBlank()) {
            throw exception(LABEL_REPRINT_REASON_REQUIRED);
        }
        long previous = printLogMapper.selectCount(RdLabelPrintLogDO::getPackageId, packageId);
        printLogMapper.insert(new RdLabelPrintLogDO().setPackageId(packageId).setTemplateCode("GOODS_CARD")
                .setPrintSequence((int) previous + 1).setReprint(!firstPrint).setReprintReason(reprintReason)
                .setPrinterId(operatorId).setPrinterName(operatorName).setPrintTime(LocalDateTime.now()));
    }

    @Override public RdPackageUnitDO get(Long id) {
        RdPackageUnitDO unit = packageMapper.selectById(id);
        if (unit == null) throw exception(PACKAGE_NOT_EXISTS);
        return unit;
    }
    @Override public List<RdPackageUnitDO> getByProductionOutputId(Long productionOutputId) {
        return packageMapper.selectListByProductionOutputId(productionOutputId);
    }
    @Override public List<RdPackageUnitDO> trace(String packageCode, Long productionOutputId) {
        if (packageCode != null && !packageCode.isBlank()) {
            RdPackageUnitDO unit = packageMapper.selectByPackageCode(packageCode.trim());
            return unit == null ? Collections.emptyList() : List.of(unit);
        }
        return productionOutputId == null ? Collections.emptyList()
                : packageMapper.selectListByProductionOutputId(productionOutputId);
    }
    @Override public PageResult<RdPackageUnitDO> page(PageParam pageParam) {
        return packageMapper.selectPage(pageParam, new LambdaQueryWrapperX<RdPackageUnitDO>()
                .orderByDesc(RdPackageUnitDO::getId));
    }
}
