package com.ruiding.erp.service.production;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputPageReqVO;
import com.ruiding.erp.controller.admin.production.vo.RdProductionOutputSaveReqVO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionTaskDO;
import com.ruiding.erp.dal.mysql.production.RdProductionTaskMapper;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.dal.mysql.ordersplit.RdOrderSplitOutputBindMapper;
import com.ruiding.erp.enums.RdLogRecordConstants;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import com.ruiding.erp.service.stock.RdStockLotService;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageUnitMapper;
import com.ruiding.erp.service.masterdata.RdCustomerProductPair;
import com.ruiding.erp.service.masterdata.RdCustomerProductService;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_CANCEL_FAIL_CANCELLED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_CANCEL_FAIL_INBOUNDED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_INBOUND_FAIL_CANCELLED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_INBOUND_FAIL_INBOUNDED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_PACKAGE_REQUIRED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_PACKAGE_LOCKED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_USE_INBOUND_ORDER;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_NOT_EXISTS;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_UPDATE_FAIL_INBOUNDED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_BOUND_LOCKED;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_CONFIRM_STATUS_INVALID;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCTION_OUTPUT_VERSION_CONFLICT;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

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
    @Resource
    private RdPackageUnitMapper packageUnitMapper;
    @Resource
    private RdCustomerProductService customerProductService;
    @Resource
    private RdOrderSplitOutputBindMapper orderSplitOutputBindMapper;
    @Resource private RdProductionTaskMapper productionTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TYPE,
            subType = RdLogRecordConstants.PRODUCTION_CREATE_SUB_TYPE,
            bizNo = RdLogRecordConstants.BIZ_ID_EXPRESSION,
            success = RdLogRecordConstants.PRODUCTION_CREATE_SUCCESS)
    public Long createProductionOutput(RdProductionOutputSaveReqVO createReqVO) {
        RdProductionTaskDO task = createReqVO.getProductionTaskId() == null ? null :
                requireTask(createReqVO.getProductionTaskId());
        if (StrUtil.isNotBlank(createReqVO.getClientRequestId())) {
            RdProductionOutputDO replay = productionOutputMapper.selectByClientRequestId(createReqVO.getClientRequestId());
            if (replay != null) return validateReplay(createReqVO, replay);
        }
        String requestDigest = null;
        if (task != null) {
            if (StrUtil.isBlank(createReqVO.getClientRequestId())) throw exception(PRODUCTION_TASK_REQUEST_REQUIRED);
            validateTaskFields(createReqVO, task);
            requestDigest = taskOutputDigest(createReqVO, task.getShiftCode());
            requireActive(task);
        }
        RdCustomerProductPair pair = customerProductService.recordPair(
                createReqVO.getCustomerName(), createReqVO.getProductModel());
        RdProductionOutputDO productionOutput = BeanUtils.toBean(createReqVO, RdProductionOutputDO.class);
        productionOutput.setCustomerName(pair.getCustomerName()).setProductModel(pair.getProductModel());
        productionOutput.setOperatorId(getLoginUserId()).setOperatorName(getLoginUserNickname());
        if (task != null) {
            productionOutput.setId(null).setProductionTaskNo(task.getTaskNo()).setShiftCode(task.getShiftCode())
                    .setRequestDigest(requestDigest).setStatus("DRAFT").setInboundQty(BigDecimal.ZERO);
        } else {
            productionOutput.setClientRequestId(null);
        }
        fillCalculatedFields(productionOutput);
        try {
            productionOutputMapper.insert(productionOutput);
        } catch (DuplicateKeyException error) {
            throw exception(PRODUCTION_TASK_REQUEST_CONFLICT);
        }
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, productionOutput.getId());
        return productionOutput.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TYPE,
            subType = RdLogRecordConstants.PRODUCTION_UPDATE_SUB_TYPE,
            bizNo = "{{#updateReqVO.id}}", success = RdLogRecordConstants.PRODUCTION_UPDATE_SUCCESS)
    public void updateProductionOutput(RdProductionOutputSaveReqVO updateReqVO) {
        RdProductionOutputDO before = productionOutputMapper.selectById(updateReqVO.getId());
        if (before == null) throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        Long taskId = before.getProductionTaskId() == null ? updateReqVO.getProductionTaskId() : before.getProductionTaskId();
        if (before.getProductionTaskId() != null && updateReqVO.getProductionTaskId() != null
                && !before.getProductionTaskId().equals(updateReqVO.getProductionTaskId())) {
            throw exception(PRODUCTION_TASK_BINDING_LOCKED);
        }
        RdProductionTaskDO task = taskId == null ? null : requireTask(taskId);
        if (task != null) {
            requireActive(task);
            validateTaskFields(updateReqVO, task);
        }
        RdProductionOutputDO oldProductionOutput = lockProductionOutput(updateReqVO.getId(), updateReqVO.getVersion());
        if (!Objects.equals(before.getProductionTaskId(), oldProductionOutput.getProductionTaskId())) throw exception(PRODUCTION_OUTPUT_VERSION_CONFLICT);
        if (task != null && !"DRAFT".equals(oldProductionOutput.getStatus())) throw exception(PRODUCTION_TASK_OUTPUT_INVALID);
        if (updateReqVO.getClientRequestId() != null
                && !Objects.equals(oldProductionOutput.getClientRequestId(), updateReqVO.getClientRequestId())) {
            throw exception(PRODUCTION_TASK_REQUEST_CONFLICT);
        }
        validateNotActivelyBound(updateReqVO.getId());
        validatePackageNotGenerated(updateReqVO.getId());
        if (RdProductionOutputStatusEnum.INBOUNDED.getStatus().equals(oldProductionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_UPDATE_FAIL_INBOUNDED);
        }
        RdProductionOutputDO updateObj = BeanUtils.toBean(updateReqVO, RdProductionOutputDO.class);
        RdCustomerProductPair pair = customerProductService.recordPair(
                updateReqVO.getCustomerName(), updateReqVO.getProductModel());
        updateObj.setCustomerName(pair.getCustomerName()).setProductModel(pair.getProductModel());
        updateObj.setClientRequestId(oldProductionOutput.getClientRequestId()).setRequestDigest(oldProductionOutput.getRequestDigest());
        if (task != null) {
            updateObj.setProductionTaskId(task.getId()).setProductionTaskNo(task.getTaskNo()).setShiftCode(task.getShiftCode());
        }
        if (StrUtil.isBlank(updateObj.getStatus())) {
            updateObj.setStatus(oldProductionOutput.getStatus());
        }
        updateObj.setVersion(oldProductionOutput.getVersion() + 1);
        fillCalculatedFields(updateObj);
        productionOutputMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    @LogRecord(type = RdLogRecordConstants.PRODUCTION_TYPE,
            subType = RdLogRecordConstants.PRODUCTION_CANCEL_SUB_TYPE,
            bizNo = "{{#id}}", success = RdLogRecordConstants.PRODUCTION_CANCEL_SUCCESS)
    public void cancelProductionOutput(Long id, Integer version) {
        Long expectedTaskId = lockLinkedTask(id);
        RdProductionOutputDO productionOutput = lockProductionOutput(id, version);
        if (!Objects.equals(expectedTaskId, productionOutput.getProductionTaskId())) throw exception(PRODUCTION_OUTPUT_VERSION_CONFLICT);
        validateNotActivelyBound(id);
        validatePackageNotGenerated(id);
        if (RdProductionOutputStatusEnum.INBOUNDED.getStatus().equals(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_CANCEL_FAIL_INBOUNDED);
        }
        if (RdProductionOutputStatusEnum.CANCELLED.getStatus().equals(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_CANCEL_FAIL_CANCELLED);
        }
        productionOutputMapper.updateById(new RdProductionOutputDO()
                .setId(id)
                .setStatus(RdProductionOutputStatusEnum.CANCELLED.getStatus())
                .setVersion(productionOutput.getVersion() + 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void confirmProductionOutput(Long id, Integer version) {
        Long expectedTaskId = lockLinkedTask(id);
        RdProductionOutputDO productionOutput = lockProductionOutput(id, version);
        if (!Objects.equals(expectedTaskId, productionOutput.getProductionTaskId())) throw exception(PRODUCTION_OUTPUT_VERSION_CONFLICT);
        if (!RdProductionOutputStatusEnum.DRAFT.getStatus().equals(productionOutput.getStatus())) {
            throw exception(PRODUCTION_OUTPUT_CONFIRM_STATUS_INVALID);
        }
        productionOutputMapper.updateById(new RdProductionOutputDO().setId(id)
                .setStatus(RdProductionOutputStatusEnum.CONFIRMED.getStatus()).setVersion(version + 1));
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
        long packageCount = packageUnitMapper == null ? 0 : packageUnitMapper.selectCountByProductionOutputId(id);
        if (packageCount == 0) {
            throw exception(PRODUCTION_OUTPUT_PACKAGE_REQUIRED);
        }
        throw exception(PRODUCTION_OUTPUT_USE_INBOUND_ORDER);
    }

    private RdProductionOutputDO validateProductionOutputExists(Long id) {
        RdProductionOutputDO productionOutput = productionOutputMapper.selectById(id);
        if (productionOutput == null) {
            throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        }
        return productionOutput;
    }

    private RdProductionTaskDO requireTask(Long id) {
        RdProductionTaskDO task = productionTaskMapper.selectByIdForUpdate(id);
        if (task == null) throw exception(PRODUCTION_TASK_NOT_EXISTS);
        return task;
    }

    private void requireActive(RdProductionTaskDO task) {
        if (!"ACTIVE".equals(task.getStatus())) throw exception(PRODUCTION_TASK_NOT_ACTIVE);
    }

    private Long lockLinkedTask(Long id) {
        RdProductionOutputDO before = productionOutputMapper.selectById(id);
        if (before == null) throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        if (before.getProductionTaskId() != null) requireActive(requireTask(before.getProductionTaskId()));
        return before.getProductionTaskId();
    }

    private void validateTaskFields(RdProductionOutputSaveReqVO req, RdProductionTaskDO task) {
        if (!Objects.equals(task.getProductionDate(), req.getProductionDate())
                || !task.getCustomerName().equals(req.getCustomerName().trim())
                || StrUtil.isNotBlank(req.getShiftCode()) && !task.getShiftCode().equals(req.getShiftCode().trim())) {
            throw exception(PRODUCTION_TASK_BINDING_INVALID);
        }
        if (req.getOutputQty() == null || req.getOutputQty().signum() <= 0
                || req.getOutputQty().stripTrailingZeros().scale() > 0
                || req.getLocationId() != null || StrUtil.isNotBlank(req.getLocationCode())
                || req.getInboundQty() != null && req.getInboundQty().signum() != 0
                || StrUtil.isNotBlank(req.getStatus()) && !"DRAFT".equals(req.getStatus())) {
            throw exception(PRODUCTION_TASK_OUTPUT_INVALID);
        }
    }

    private Long validateReplay(RdProductionOutputSaveReqVO req, RdProductionOutputDO replay) {
        if (!Objects.equals(replay.getProductionTaskId(), req.getProductionTaskId())
                || !Objects.equals(replay.getRequestDigest(), taskOutputDigest(req, replay.getShiftCode()))) {
            throw exception(PRODUCTION_TASK_REQUEST_CONFLICT);
        }
        LogRecordContext.putVariable(RdLogRecordConstants.BIZ_ID_VARIABLE, replay.getId());
        return replay.getId();
    }

    private String taskOutputDigest(RdProductionOutputSaveReqVO req, String defaultShift) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(req.getProductionTaskId(), req.getCustomerName().trim(),
                req.getProductionDate(), StrUtil.isBlank(req.getShiftCode()) ? defaultShift : req.getShiftCode().trim(), req.getProductModel().trim(), req.getFurnaceNo().trim(),
                req.getFurnaceLayer().trim(), req.getOutputQty().stripTrailingZeros().toPlainString(),
                req.getPackageWeight().stripTrailingZeros().toPlainString(), req.getFurnaceOutDate(),
                req.getSourceSheet(), req.getRemark(), req.getLocationId(),
                StrUtil.isBlank(req.getLocationCode()) ? null : req.getLocationCode().trim(),
                req.getInboundQty() == null ? "0" : req.getInboundQty().stripTrailingZeros().toPlainString(),
                StrUtil.isBlank(req.getStatus()) ? "DRAFT" : req.getStatus(), getLoginUserId())));
    }

    private RdProductionOutputDO lockProductionOutput(Long id, Integer version) {
        RdProductionOutputDO productionOutput = productionOutputMapper.selectByIdForUpdate(id);
        if (productionOutput == null) {
            throw exception(PRODUCTION_OUTPUT_NOT_EXISTS);
        }
        if (!java.util.Objects.equals(productionOutput.getVersion(), version)) {
            throw exception(PRODUCTION_OUTPUT_VERSION_CONFLICT);
        }
        return productionOutput;
    }

    private void validatePackageNotGenerated(Long productionOutputId) {
        if (packageUnitMapper.selectCountByProductionOutputId(productionOutputId) > 0) {
            throw exception(PRODUCTION_OUTPUT_PACKAGE_LOCKED);
        }
    }

    private void validateNotActivelyBound(Long productionOutputId) {
        if (orderSplitOutputBindMapper != null
                && orderSplitOutputBindMapper.selectActiveByProductionOutputId(productionOutputId) != null) {
            throw exception(PRODUCTION_OUTPUT_BOUND_LOCKED);
        }
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
