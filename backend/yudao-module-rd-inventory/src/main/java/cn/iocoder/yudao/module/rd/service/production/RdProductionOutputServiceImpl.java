package cn.iocoder.yudao.module.rd.service.production;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rd.controller.admin.production.vo.RdProductionOutputPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.production.vo.RdProductionOutputSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;
import cn.iocoder.yudao.module.rd.dal.mysql.production.RdProductionOutputMapper;
import cn.iocoder.yudao.module.rd.enums.RdLogRecordConstants;
import cn.iocoder.yudao.module.rd.enums.RdProductionOutputStatusEnum;
import cn.iocoder.yudao.module.rd.service.stock.RdStockLotService;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_CANCEL_FAIL_CANCELLED;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_CANCEL_FAIL_INBOUNDED;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_INBOUND_FAIL_CANCELLED;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_INBOUND_FAIL_INBOUNDED;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_INBOUND_FAIL_LOCATION_EMPTY;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_NOT_EXISTS;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_STATUS_INVALID;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_UPDATE_FAIL_INBOUNDED;

/**
 * RD furnace-layer production output Service implementation.
 */
@Service
@Validated
public class RdProductionOutputServiceImpl implements RdProductionOutputService {

    @Resource
    private RdProductionOutputMapper productionOutputMapper;
    @Resource
    private RdStockLotService stockLotService;

    @Override
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TYPE,
            subType = RdLogRecordConstants.PRODUCTION_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.PRODUCTION_CREATE_SUCCESS)
    public Long createProductionOutput(RdProductionOutputSaveReqVO createReqVO) {
        RdProductionOutputDO productionOutput = BeanUtils.toBean(createReqVO, RdProductionOutputDO.class);
        fillCalculatedFields(productionOutput);
        productionOutputMapper.insert(productionOutput);
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, productionOutput.getId());
        return productionOutput.getId();
    }

    @Override
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TYPE,
            subType = RdLogRecordConstants.PRODUCTION_UPDATE_SUB_TYPE,
            bizNo = "{{#updateReqVO.id}}", success = RdLogRecordConstants.PRODUCTION_UPDATE_SUCCESS)
    public void updateProductionOutput(RdProductionOutputSaveReqVO updateReqVO) {
        RdProductionOutputDO oldProductionOutput = validateProductionOutputExists(updateReqVO.getId());
        if (RdProductionOutputStatusEnum.INBOUNDED.getStatus().equals(oldProductionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_UPDATE_FAIL_INBOUNDED);
        }
        RdProductionOutputDO updateObj = BeanUtils.toBean(updateReqVO, RdProductionOutputDO.class);
        if (StrUtil.isBlank(updateObj.getStatus())) {
            updateObj.setStatus(oldProductionOutput.getStatus());
        }
        fillCalculatedFields(updateObj);
        productionOutputMapper.updateById(updateObj);
    }

    @Override
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TYPE,
            subType = RdLogRecordConstants.PRODUCTION_CANCEL_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.PRODUCTION_CANCEL_SUCCESS)
    public void cancelProductionOutput(Long id) {
        RdProductionOutputDO productionOutput = validateProductionOutputExists(id);
        if (RdProductionOutputStatusEnum.INBOUNDED.getStatus().equals(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_CANCEL_FAIL_INBOUNDED);
        }
        if (RdProductionOutputStatusEnum.CANCELLED.getStatus().equals(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_CANCEL_FAIL_CANCELLED);
        }
        productionOutputMapper.updateById(new RdProductionOutputDO()
                .setId(id)
                .setStatus(RdProductionOutputStatusEnum.CANCELLED.getStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TYPE,
            subType = RdLogRecordConstants.PRODUCTION_INBOUND_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.PRODUCTION_INBOUND_SUCCESS)
    public void inboundProductionOutput(Long id) {
        RdProductionOutputDO productionOutput = validateProductionOutputExists(id);
        if (RdProductionOutputStatusEnum.INBOUNDED.getStatus().equals(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_INBOUND_FAIL_INBOUNDED);
        }
        if (RdProductionOutputStatusEnum.CANCELLED.getStatus().equals(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_INBOUND_FAIL_CANCELLED);
        }
        if (StrUtil.isBlank(productionOutput.getLocationCode())) {
            throw exception(PRODUCTION_OUTPUT_INBOUND_FAIL_LOCATION_EMPTY);
        }
        stockLotService.createProductionInbound(productionOutput);
        productionOutputMapper.updateById(new RdProductionOutputDO()
                .setId(id)
                .setInboundQty(productionOutput.getOutputQty())
                .setStatus(RdProductionOutputStatusEnum.INBOUNDED.getStatus()));
    }

    private RdProductionOutputDO validateProductionOutputExists(Long id) {
        RdProductionOutputDO productionOutput = productionOutputMapper.selectById(id);
        if (productionOutput == null) {
            throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        }
        return productionOutput;
    }

    @Override
    public RdProductionOutputDO getProductionOutput(Long id) {
        return productionOutputMapper.selectById(id);
    }

    @Override
    public PageResult<RdProductionOutputDO> getProductionOutputPage(RdProductionOutputPageReqVO pageReqVO) {
        return productionOutputMapper.selectPage(pageReqVO);
    }

    private void fillCalculatedFields(RdProductionOutputDO productionOutput) {
        if (StrUtil.isBlank(productionOutput.getStatus())) {
            productionOutput.setStatus(RdProductionOutputStatusEnum.DRAFT.getStatus());
        }
        if (!RdProductionOutputStatusEnum.isValid(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_STATUS_INVALID);
        }
        if (productionOutput.getInboundQty() == null) {
            productionOutput.setInboundQty(BigDecimal.ZERO);
        }
        productionOutput.setOutputWeight(productionOutput.getOutputQty().multiply(productionOutput.getPackageWeight()));
    }

}
