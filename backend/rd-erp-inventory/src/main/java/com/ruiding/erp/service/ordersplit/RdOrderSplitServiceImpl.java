package com.ruiding.erp.service.ordersplit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import com.ruiding.erp.controller.admin.ordersplit.vo.*;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.ordersplit.*;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.dal.mysql.ordersplit.*;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.enums.RdOrderStatusEnum;
import com.ruiding.erp.enums.RdProductionOutputStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.*;

@Service
@Validated
public class RdOrderSplitServiceImpl implements RdOrderSplitService {

    @Resource private RdOrderSplitRuleMapper ruleMapper;
    @Resource private RdOrderSplitRuleLineMapper ruleLineMapper;
    @Resource private RdOrderSplitResultMapper resultMapper;
    @Resource private RdOrderSplitResultLineMapper resultLineMapper;
    @Resource private RdOrderSplitOutputBindMapper bindMapper;
    @Resource private RdSaleOrderMapper saleOrderMapper;
    @Resource private RdProductionOutputMapper productionOutputMapper;
    private final RdOrderSplitCalculator calculator = new RdOrderSplitCalculator();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRule(RdOrderSplitRuleSaveReqVO reqVO) {
        validateRule(reqVO);
        RdOrderSplitRuleDO rule = BeanUtils.toBean(reqVO, RdOrderSplitRuleDO.class).setId(null).setVersion(1);
        ruleMapper.insert(rule);
        replaceRuleLines(rule.getId(), reqVO.getLines());
        return rule.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRule(RdOrderSplitRuleSaveReqVO reqVO) {
        validateRule(reqVO);
        RdOrderSplitRuleDO old = ruleMapper.selectByIdForUpdate(reqVO.getId());
        if (old == null) throw exception(ORDER_SPLIT_RULE_NOT_EXISTS);
        checkVersion(old.getVersion(), reqVO.getVersion());
        RdOrderSplitRuleDO update = BeanUtils.toBean(reqVO, RdOrderSplitRuleDO.class)
                .setVersion(old.getVersion() + 1);
        ruleMapper.updateById(update);
        replaceRuleLines(old.getId(), reqVO.getLines());
    }

    @Override
    public Map<String, Object> getRule(Long id) {
        RdOrderSplitRuleDO rule = ruleMapper.selectById(id);
        if (rule == null) throw exception(ORDER_SPLIT_RULE_NOT_EXISTS);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("rule", rule);
        detail.put("lines", ruleLineMapper.selectByRuleId(id));
        return detail;
    }

    @Override
    public PageResult<RdOrderSplitRuleDO> getRulePage(RdOrderSplitRulePageReqVO reqVO) {
        return ruleMapper.selectPage(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(RdOrderSplitCreateReqVO reqVO) {
        RdSaleOrderDO order = saleOrderMapper.selectByIdForUpdate(reqVO.getSaleOrderId());
        if (order == null) throw exception(SALE_ORDER_NOT_EXISTS);
        RdOrderSplitRuleDO rule = ruleMapper.selectById(reqVO.getRuleId());
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) throw exception(ORDER_SPLIT_RULE_NOT_EXISTS);
        if (!Objects.equals(order.getCustomerName(), rule.getCustomerName())
                || !Objects.equals(order.getProductModel(), rule.getProductModel())) {
            throw exception(ORDER_SPLIT_BIND_INVALID);
        }
        List<RdOrderSplitRuleLineDO> ruleLines = ruleLineMapper.selectByRuleId(rule.getId());
        RdOrderSplitCalculation calculation = calculator.calculate(reqVO.getFurnaceLoadQty(), reqVO.getYieldRatePercent(),
                ruleLines.stream().map(line -> new RdOrderSplitAllocation(line.getAllocationType(), line.getAllocationValue()))
                        .collect(Collectors.toList()));
        RdOrderSplitResultDO result = new RdOrderSplitResultDO()
                .setSplitNo("SP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")))
                .setSplitDate(reqVO.getSplitDate()).setSaleOrderId(order.getId()).setSaleOrderNo(order.getOrderNo())
                .setRuleId(rule.getId()).setRuleNameSnapshot(rule.getRuleName())
                .setCustomerNameSnapshot(order.getCustomerName()).setProductModelSnapshot(order.getProductModel())
                .setFurnaceLoadQty(reqVO.getFurnaceLoadQty()).setYieldRatePercent(reqVO.getYieldRatePercent())
                .setExpectedOutputQty(calculation.getExpectedOutputQty())
                .setFinalPlannedOutputQty(calculation.getFinalPlannedOutputQty())
                .setVarianceQty(calculation.getVarianceQty()).setStatus("DRAFT").setVersion(1).setRemark(reqVO.getRemark());
        resultMapper.insert(result);
        for (int i = 0; i < ruleLines.size(); i++) {
            RdOrderSplitRuleLineDO source = ruleLines.get(i);
            resultLineMapper.insert(new RdOrderSplitResultLineDO().setResultId(result.getId())
                    .setSequenceNo(source.getSequenceNo()).setFurnaceLayer(source.getFurnaceLayer())
                    .setAllocationTypeSnapshot(source.getAllocationType()).setAllocationValueSnapshot(source.getAllocationValue())
                    .setCalculatedBaseQty(calculation.getBaseQuantities().get(i)).setManualAdjustmentQty(zero())
                    .setFinalPlannedQty(calculation.getFinalQuantities().get(i)).setActualBoundQty(zero()));
        }
        return result.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjust(RdOrderSplitAdjustReqVO reqVO) {
        RdOrderSplitResultDO result = lockResult(reqVO.getId(), reqVO.getVersion(), "DRAFT");
        List<RdOrderSplitResultLineDO> lines = resultLineMapper.selectByResultId(result.getId());
        Map<Long, RdOrderSplitAdjustReqVO.Line> adjustments = reqVO.getLines().stream()
                .collect(Collectors.toMap(RdOrderSplitAdjustReqVO.Line::getLineId, Function.identity()));
        List<BigDecimal> values = new ArrayList<>();
        for (RdOrderSplitResultLineDO line : lines) {
            RdOrderSplitAdjustReqVO.Line adjustment = adjustments.get(line.getId());
            BigDecimal value = adjustment == null ? zero() : adjustment.getManualAdjustmentQty();
            values.add(value);
        }
        if (!adjustments.keySet().stream().allMatch(id -> lines.stream().anyMatch(line -> id.equals(line.getId())))) {
            throw exception(ORDER_SPLIT_STATUS_INVALID);
        }
        RdOrderSplitCalculation calculation = calculator.applyAdjustments(result.getExpectedOutputQty(),
                lines.stream().map(RdOrderSplitResultLineDO::getCalculatedBaseQty).collect(Collectors.toList()), values);
        for (int i = 0; i < lines.size(); i++) {
            RdOrderSplitAdjustReqVO.Line adjustment = adjustments.get(lines.get(i).getId());
            resultLineMapper.updateById(new RdOrderSplitResultLineDO().setId(lines.get(i).getId())
                    .setManualAdjustmentQty(values.get(i)).setFinalPlannedQty(calculation.getFinalQuantities().get(i))
                    .setAdjustmentReason(adjustment == null ? null : adjustment.getAdjustmentReason()));
        }
        resultMapper.updateById(new RdOrderSplitResultDO().setId(result.getId())
                .setFinalPlannedOutputQty(calculation.getFinalPlannedOutputQty()).setVarianceQty(calculation.getVarianceQty())
                .setVersion(result.getVersion() + 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(RdOrderSplitActionReqVO reqVO, Long userId, String userName) {
        RdOrderSplitResultDO result = lockResult(reqVO.getId(), reqVO.getVersion(), "DRAFT");
        resultMapper.updateById(new RdOrderSplitResultDO().setId(result.getId()).setStatus("CONFIRMED")
                .setVersion(result.getVersion() + 1).setConfirmerId(userId).setConfirmerName(userName)
                .setConfirmTime(LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(RdOrderSplitActionReqVO reqVO, Long userId, String userName) {
        RdOrderSplitResultDO result = lockResult(reqVO.getId(), reqVO.getVersion(), "DRAFT", "CONFIRMED");
        if (!bindMapper.selectActiveByOrderId(result.getSaleOrderId()).stream()
                .filter(bind -> result.getId().equals(bind.getResultId())).collect(Collectors.toList()).isEmpty()) {
            throw exception(ORDER_SPLIT_STATUS_INVALID);
        }
        resultMapper.updateById(new RdOrderSplitResultDO().setId(result.getId()).setStatus("CANCELLED")
                .setVersion(result.getVersion() + 1).setCancellerId(userId).setCancellerName(userName)
                .setCancelTime(LocalDateTime.now()).setCancelReason(reqVO.getReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindOutput(RdOrderSplitBindReqVO reqVO, Long userId, String userName) {
        RdOrderSplitResultDO result = lockResult(reqVO.getId(), reqVO.getVersion(), "CONFIRMED");
        RdOrderSplitResultLineDO line = resultLineMapper.selectByIdForUpdate(reqVO.getResultLineId());
        RdProductionOutputDO output = productionOutputMapper.selectByIdForUpdate(reqVO.getProductionOutputId());
        if (line == null || !result.getId().equals(line.getResultId()) || output == null
                || !(RdProductionOutputStatusEnum.CONFIRMED.getStatus().equals(output.getStatus())
                || RdProductionOutputStatusEnum.INBOUNDED.getStatus().equals(output.getStatus()))
                || !Objects.equals(result.getCustomerNameSnapshot(), output.getCustomerName())
                || !Objects.equals(result.getProductModelSnapshot(), output.getProductModel())
                || !Objects.equals(line.getFurnaceLayer(), output.getFurnaceLayer())) {
            throw exception(ORDER_SPLIT_BIND_INVALID);
        }
        RdOrderSplitOutputBindDO existing = bindMapper.selectByProductionOutputIdForUpdate(output.getId());
        if (existing != null && "ACTIVE".equals(existing.getStatus())) throw exception(ORDER_SPLIT_OUTPUT_BOUND);
        RdOrderSplitOutputBindDO bind = existing == null ? new RdOrderSplitOutputBindDO() : existing;
        bind.setSaleOrderId(result.getSaleOrderId()).setResultId(result.getId()).setResultLineId(line.getId())
                .setProductionOutputId(output.getId()).setOutputQtySnapshot(output.getOutputQty()).setStatus("ACTIVE")
                .setBinderId(userId).setBinderName(userName).setBindTime(LocalDateTime.now())
                .setUnbinderId(null).setUnbinderName(null).setUnbindTime(null).setUnbindReason(null);
        if (existing == null) bindMapper.insert(bind); else bindMapper.updateById(bind);
        refreshAfterBinding(result, line);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindOutput(RdOrderSplitUnbindReqVO reqVO, Long userId, String userName) {
        RdOrderSplitResultDO result = lockResult(reqVO.getId(), reqVO.getVersion(), "CONFIRMED");
        RdOrderSplitOutputBindDO bind = bindMapper.selectByProductionOutputIdForUpdate(reqVO.getProductionOutputId());
        if (bind == null || !"ACTIVE".equals(bind.getStatus()) || !result.getId().equals(bind.getResultId())) {
            throw exception(ORDER_SPLIT_BIND_INVALID);
        }
        bindMapper.updateById(new RdOrderSplitOutputBindDO().setId(bind.getId()).setStatus("CANCELLED")
                .setUnbinderId(userId).setUnbinderName(userName).setUnbindTime(LocalDateTime.now())
                .setUnbindReason(reqVO.getReason()));
        refreshAfterBinding(result, resultLineMapper.selectByIdForUpdate(bind.getResultLineId()));
    }

    private void refreshAfterBinding(RdOrderSplitResultDO result, RdOrderSplitResultLineDO line) {
        resultLineMapper.updateById(new RdOrderSplitResultLineDO().setId(line.getId())
                .setActualBoundQty(bindMapper.sumActiveQtyByResultLineId(line.getId())));
        resultMapper.updateById(new RdOrderSplitResultDO().setId(result.getId()).setVersion(result.getVersion() + 1));
        recomputeSaleOrderOutput(result.getSaleOrderId());
    }

    public void recomputeSaleOrderOutput(Long orderId) {
        RdSaleOrderDO order = saleOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) throw exception(SALE_ORDER_NOT_EXISTS);
        BigDecimal manual = nullableZero(order.getManualOutputQty());
        BigDecimal split = bindMapper.selectActiveByOrderId(orderId).stream()
                .map(RdOrderSplitOutputBindDO::getOutputQtySnapshot).map(RdOrderSplitServiceImpl::nullableZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = manual.add(split);
        BigDecimal required = nullableZero(order.getRequiredQty());
        BigDecimal fulfilled = actual.add(nullableZero(order.getAllocatedQty()))
                .add(nullableZero(order.getTransferInQty()))
                .subtract(nullableZero(order.getTransferOutQty()));
        if (fulfilled.signum() < 0) {
            fulfilled = BigDecimal.ZERO;
        }
        saleOrderMapper.updateById(new RdSaleOrderDO().setId(orderId).setManualOutputQty(manual)
                .setSplitOutputQty(split).setActualOutputQty(actual)
                .setDifferenceQty(required.subtract(actual))
                .setFulfilledQty(fulfilled)
                .setAdjustedDifferenceQty(required.subtract(fulfilled))
                .setStatus(resolveOrderStatus(required, fulfilled)));
    }

    private static String resolveOrderStatus(BigDecimal required, BigDecimal fulfilled) {
        if (fulfilled.compareTo(required) >= 0) {
            return RdOrderStatusEnum.FULFILLED.getStatus();
        }
        if (fulfilled.signum() > 0) {
            return RdOrderStatusEnum.PARTIAL.getStatus();
        }
        return RdOrderStatusEnum.OPEN.getStatus();
    }

    @Override
    public RdOrderSplitDetailRespVO getDetail(Long id) {
        RdOrderSplitResultDO result = resultMapper.selectById(id);
        if (result == null) throw exception(ORDER_SPLIT_NOT_EXISTS);
        List<RdOrderSplitResultLineDO> lines = resultLineMapper.selectByResultId(id);
        result.setActualBoundOutputQty(lines.stream().map(RdOrderSplitResultLineDO::getActualBoundQty)
                .map(RdOrderSplitServiceImpl::nullableZero).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<RdOrderSplitOutputBindDO> bindings = bindMapper.selectByResultId(id);
        Set<Long> outputIds = bindings.stream().map(RdOrderSplitOutputBindDO::getProductionOutputId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, RdProductionOutputDO> outputMap = outputIds.isEmpty() ? Collections.emptyMap()
                : productionOutputMapper.selectByIds(outputIds).stream()
                        .collect(Collectors.toMap(RdProductionOutputDO::getId, Function.identity()));
        List<RdOrderSplitOutputBindRespVO> bindingResponses = BeanUtils.toBean(bindings, RdOrderSplitOutputBindRespVO.class);
        bindingResponses.forEach(binding -> {
            RdProductionOutputDO output = outputMap.get(binding.getProductionOutputId());
            if (output != null) {
                binding.setFurnaceNo(output.getFurnaceNo()).setFurnaceLayer(output.getFurnaceLayer())
                        .setProductionDate(output.getProductionDate());
            }
        });
        return new RdOrderSplitDetailRespVO(result, lines, bindingResponses);
    }

    @Override
    public PageResult<RdOrderSplitResultDO> getPage(RdOrderSplitPageReqVO reqVO) {
        PageResult<RdOrderSplitResultDO> page = resultMapper.selectPage(reqVO);
        if (page.getList().isEmpty()) return page;
        Map<Long, BigDecimal> boundQtyMap = bindMapper.selectActiveQtyByResultIds(page.getList().stream()
                        .map(RdOrderSplitResultDO::getId).collect(Collectors.toList())).stream()
                .collect(Collectors.toMap(RdOrderSplitOutputBindDO::getResultId,
                        bind -> nullableZero(bind.getOutputQtySnapshot())));
        page.getList().forEach(result -> result.setActualBoundOutputQty(
                boundQtyMap.getOrDefault(result.getId(), BigDecimal.ZERO)));
        return page;
    }

    @Override
    public List<RdProductionOutputDO> getProductionOptions(Long resultLineId) {
        RdOrderSplitResultLineDO line = resultLineMapper.selectById(resultLineId);
        if (line == null) throw exception(ORDER_SPLIT_NOT_EXISTS);
        RdOrderSplitResultDO result = resultMapper.selectById(line.getResultId());
        if (result == null || !"CONFIRMED".equals(result.getStatus())) throw exception(ORDER_SPLIT_STATUS_INVALID);
        return productionOutputMapper.selectBindingOptions(result.getCustomerNameSnapshot(), result.getProductModelSnapshot(),
                line.getFurnaceLayer()).stream().filter(output -> bindMapper.selectActiveByProductionOutputId(output.getId()) == null)
                .collect(Collectors.toList());
    }

    private RdOrderSplitResultDO lockResult(Long id, Integer version, String... statuses) {
        RdOrderSplitResultDO result = resultMapper.selectByIdForUpdate(id);
        if (result == null) throw exception(ORDER_SPLIT_NOT_EXISTS);
        checkVersion(result.getVersion(), version);
        if (!Arrays.asList(statuses).contains(result.getStatus())) throw exception(ORDER_SPLIT_STATUS_INVALID);
        return result;
    }

    private static void checkVersion(Integer actual, Integer requested) {
        if (!Objects.equals(actual, requested)) throw exception(ORDER_SPLIT_VERSION_CONFLICT);
    }

    private void validateRule(RdOrderSplitRuleSaveReqVO reqVO) {
        Set<Integer> sequences = new HashSet<>();
        Set<String> layers = new HashSet<>();
        for (RdOrderSplitRuleSaveReqVO.Line line : reqVO.getLines()) {
            if (!("FIXED_QTY".equals(line.getAllocationType()) || "RATIO".equals(line.getAllocationType()))
                    || !sequences.add(line.getSequenceNo()) || !layers.add(line.getFurnaceLayer().trim())) {
                throw new IllegalArgumentException("invalid or duplicate rule line");
            }
        }
        calculator.calculate(reqVO.getDefaultFurnaceLoadQty(), reqVO.getYieldRatePercent(), reqVO.getLines().stream()
                .map(line -> new RdOrderSplitAllocation(line.getAllocationType(), line.getAllocationValue()))
                .collect(Collectors.toList()));
    }

    private void replaceRuleLines(Long ruleId, List<RdOrderSplitRuleSaveReqVO.Line> lines) {
        ruleLineMapper.deleteByRuleId(ruleId);
        for (RdOrderSplitRuleSaveReqVO.Line line : lines) {
            ruleLineMapper.insert(BeanUtils.toBean(line, RdOrderSplitRuleLineDO.class).setRuleId(ruleId));
        }
    }

    private static BigDecimal zero() { return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP); }
    private static BigDecimal nullableZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
