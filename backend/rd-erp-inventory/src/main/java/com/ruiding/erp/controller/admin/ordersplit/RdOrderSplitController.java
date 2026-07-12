package com.ruiding.erp.controller.admin.ordersplit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.ordersplit.vo.*;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultDO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitRuleDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.service.ordersplit.RdOrderSplitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "管理后台 - RD 订单拆分")
@RestController
@RequestMapping("/rd/order-split")
@Validated
public class RdOrderSplitController {
    @Resource private RdOrderSplitService service;

    @GetMapping("/rule/page") @Operation(summary = "订单拆分规则分页")
    @PreAuthorize("@ss.hasPermission('rd:order-split:rule-query')")
    public CommonResult<PageResult<RdOrderSplitRuleDO>> rulePage(@Valid RdOrderSplitRulePageReqVO reqVO) {
        return success(service.getRulePage(reqVO));
    }

    @GetMapping("/rule/get") @Operation(summary = "订单拆分规则详情")
    @PreAuthorize("@ss.hasPermission('rd:order-split:rule-query')")
    public CommonResult<Map<String, Object>> ruleGet(@RequestParam("id") Long id) { return success(service.getRule(id)); }

    @PostMapping("/rule/create") @Operation(summary = "创建订单拆分规则")
    @PreAuthorize("@ss.hasPermission('rd:order-split:rule-manage')")
    public CommonResult<Long> ruleCreate(@Valid @RequestBody RdOrderSplitRuleSaveReqVO reqVO) {
        return success(service.createRule(reqVO));
    }

    @PutMapping("/rule/update") @Operation(summary = "更新订单拆分规则")
    @PreAuthorize("@ss.hasPermission('rd:order-split:rule-manage')")
    public CommonResult<Boolean> ruleUpdate(@Valid @RequestBody RdOrderSplitRuleSaveReqVO reqVO) {
        service.updateRule(reqVO); return success(true);
    }

    @GetMapping("/page") @Operation(summary = "订单拆分分页")
    @PreAuthorize("@ss.hasPermission('rd:order-split:query')")
    public CommonResult<PageResult<RdOrderSplitResultDO>> page(@Valid RdOrderSplitPageReqVO reqVO) {
        return success(service.getPage(reqVO));
    }

    @GetMapping("/detail") @Operation(summary = "订单拆分详情")
    @PreAuthorize("@ss.hasPermission('rd:order-split:query')")
    public CommonResult<RdOrderSplitDetailRespVO> detail(@RequestParam("id") Long id) { return success(service.getDetail(id)); }

    @PostMapping("/create") @Operation(summary = "创建订单拆分")
    @PreAuthorize("@ss.hasPermission('rd:order-split:create')")
    public CommonResult<Long> create(@Valid @RequestBody RdOrderSplitCreateReqVO reqVO) { return success(service.create(reqVO)); }

    @PutMapping("/adjust") @Operation(summary = "调整订单拆分行")
    @PreAuthorize("@ss.hasPermission('rd:order-split:adjust')")
    public CommonResult<Boolean> adjust(@Valid @RequestBody RdOrderSplitAdjustReqVO reqVO) {
        service.adjust(reqVO); return success(true);
    }

    @PostMapping("/confirm") @Operation(summary = "确认订单拆分")
    @PreAuthorize("@ss.hasPermission('rd:order-split:confirm')")
    public CommonResult<Boolean> confirm(@Valid @RequestBody RdOrderSplitActionReqVO reqVO) {
        service.confirm(reqVO, getLoginUserId(), getLoginUserNickname()); return success(true);
    }

    @PostMapping("/cancel") @Operation(summary = "取消订单拆分")
    @PreAuthorize("@ss.hasPermission('rd:order-split:cancel')")
    public CommonResult<Boolean> cancel(@Valid @RequestBody RdOrderSplitActionReqVO reqVO) {
        service.cancel(reqVO, getLoginUserId(), getLoginUserNickname()); return success(true);
    }

    @GetMapping("/production-options") @Operation(summary = "可绑定生产产出")
    @PreAuthorize("@ss.hasPermission('rd:order-split:query')")
    public CommonResult<List<RdProductionOutputDO>> productionOptions(@RequestParam("resultLineId") Long lineId) {
        return success(service.getProductionOptions(lineId));
    }

    @PostMapping("/bind-output") @Operation(summary = "绑定生产产出")
    @PreAuthorize("@ss.hasPermission('rd:order-split:bind')")
    public CommonResult<Boolean> bind(@Valid @RequestBody RdOrderSplitBindReqVO reqVO) {
        service.bindOutput(reqVO, getLoginUserId(), getLoginUserNickname()); return success(true);
    }

    @PostMapping("/unbind-output") @Operation(summary = "解绑生产产出")
    @PreAuthorize("@ss.hasPermission('rd:order-split:bind')")
    public CommonResult<Boolean> unbind(@Valid @RequestBody RdOrderSplitUnbindReqVO reqVO) {
        service.unbindOutput(reqVO, getLoginUserId(), getLoginUserNickname()); return success(true);
    }
}
