package cn.iocoder.yudao.module.rd.service.production;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.rd.controller.admin.production.vo.RdProductionOutputPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.production.vo.RdProductionOutputSaveReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.production.RdProductionOutputDO;
import cn.iocoder.yudao.module.rd.dal.mysql.production.RdProductionOutputMapper;
import cn.iocoder.yudao.module.rd.enums.RdProductionOutputStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_CANCEL_FAIL_CANCELLED;
import static cn.iocoder.yudao.module.rd.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_CANCEL_FAIL_INBOUNDED;
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

    @Override
    public Long createProductionOutput(RdProductionOutputSaveReqVO createReqVO) {
        RdProductionOutputDO productionOutput = BeanUtils.toBean(createReqVO, RdProductionOutputDO.class);
        fillCalculatedFields(productionOutput);
        productionOutputMapper.insert(productionOutput);
        return productionOutput.getId();
    }

    @Override
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
