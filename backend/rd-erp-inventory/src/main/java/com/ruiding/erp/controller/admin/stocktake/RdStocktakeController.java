package com.ruiding.erp.controller.admin.stocktake;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeActionReqVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeCreateReqVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeDetailRespVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeLineRespVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeOrderRespVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakePageReqVO;
import com.ruiding.erp.controller.admin.stocktake.vo.RdStocktakeScanReqVO;
import com.ruiding.erp.service.stocktake.RdStocktakeOrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@RestController
@RequestMapping("/rd/stocktake")
@Validated
public class RdStocktakeController {

    @Resource private RdStocktakeOrderService stocktakeOrderService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:create')")
    public CommonResult<Long> create(@Valid @RequestBody RdStocktakeCreateReqVO reqVO) {
        return success(stocktakeOrderService.create(reqVO.getLocationId(), reqVO.getStocktakeDate(), reqVO.getRemark()));
    }

    @PostMapping("/freeze")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:freeze')")
    public CommonResult<Boolean> freeze(@Valid @RequestBody RdStocktakeActionReqVO reqVO) {
        stocktakeOrderService.freeze(reqVO.getId(), reqVO.getVersion());
        return success(true);
    }

    @PostMapping("/scan")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:scan')")
    public CommonResult<RdStocktakeLineRespVO> scan(@Valid @RequestBody RdStocktakeScanReqVO reqVO) {
        return success(BeanUtils.toBean(
                stocktakeOrderService.scan(reqVO, getLoginUserId(), getLoginUserNickname()),
                RdStocktakeLineRespVO.class));
    }

    @PostMapping("/submit")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:submit')")
    public CommonResult<Boolean> submit(@Valid @RequestBody RdStocktakeActionReqVO reqVO) {
        stocktakeOrderService.submit(reqVO.getId(), reqVO.getVersion(), getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/approve")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:approve')")
    public CommonResult<Boolean> approve(@Valid @RequestBody RdStocktakeActionReqVO reqVO) {
        stocktakeOrderService.approve(reqVO.getId(), reqVO.getVersion(), getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/adjust")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:adjust')")
    public CommonResult<Boolean> adjust(@Valid @RequestBody RdStocktakeActionReqVO reqVO) {
        stocktakeOrderService.adjust(reqVO.getId(), reqVO.getVersion(), getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/close")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:close')")
    public CommonResult<Boolean> close(@Valid @RequestBody RdStocktakeActionReqVO reqVO) {
        stocktakeOrderService.close(reqVO.getId(), reqVO.getVersion(), getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @PostMapping("/cancel")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:cancel')")
    public CommonResult<Boolean> cancel(@Valid @RequestBody RdStocktakeActionReqVO reqVO) {
        stocktakeOrderService.cancel(reqVO.getId(), reqVO.getVersion(), getLoginUserId(), getLoginUserNickname());
        return success(true);
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:query')")
    public CommonResult<PageResult<RdStocktakeOrderRespVO>> page(@Valid RdStocktakePageReqVO reqVO) {
        return success(BeanUtils.toBean(stocktakeOrderService.page(reqVO), RdStocktakeOrderRespVO.class));
    }

    @GetMapping("/detail")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:query')")
    public CommonResult<RdStocktakeDetailRespVO> detail(@RequestParam("id") Long id) {
        return success(new RdStocktakeDetailRespVO(
                BeanUtils.toBean(stocktakeOrderService.get(id), RdStocktakeOrderRespVO.class),
                BeanUtils.toBean(stocktakeOrderService.getLines(id), RdStocktakeLineRespVO.class)));
    }

    @GetMapping("/difference")
    @PreAuthorize("@ss.hasPermission('rd:stocktake:query')")
    public CommonResult<List<RdStocktakeLineRespVO>> difference(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(stocktakeOrderService.getDifference(id), RdStocktakeLineRespVO.class));
    }
}
