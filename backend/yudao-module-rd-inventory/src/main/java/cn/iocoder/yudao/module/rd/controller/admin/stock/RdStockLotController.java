package cn.iocoder.yudao.module.rd.controller.admin.stock;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockAdjustmentCreateReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotPageReqVO;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockLotRespVO;
import cn.iocoder.yudao.module.rd.controller.admin.stock.vo.RdStockOpeningCreateReqVO;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockLotDO;
import cn.iocoder.yudao.module.rd.service.stock.RdStockLotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.EXPORT;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;

@Tag(name = "管理后台 - RD 库存台账")
@RestController
@RequestMapping("/rd/stock-lot")
@Validated
public class RdStockLotController {

    @Resource
    private RdStockLotService stockLotService;

    @PostMapping("/opening/create")
    @Operation(summary = "创建期初库存")
    @PreAuthorize("@ss.hasPermission('rd:stock-lot:opening')")
    public CommonResult<Long> createOpeningStock(@Valid @RequestBody RdStockOpeningCreateReqVO createReqVO) {
        return success(stockLotService.createOpeningStock(
                createReqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @PostMapping("/adjustment/create")
    @Operation(summary = "创建库存调整")
    @PreAuthorize("@ss.hasPermission('rd:stock-lot:adjust')")
    public CommonResult<Long> adjustStock(@Valid @RequestBody RdStockAdjustmentCreateReqVO createReqVO) {
        return success(stockLotService.adjustStock(
                createReqVO, getLoginUserId(), getLoginUserNickname()));
    }

    @GetMapping("/get")
    @Operation(summary = "获得库存批次")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('rd:stock-lot:query')")
    public CommonResult<RdStockLotRespVO> getStockLot(@RequestParam("id") Long id) {
        RdStockLotDO stockLot = stockLotService.getStockLot(id);
        return success(BeanUtils.toBean(stockLot, RdStockLotRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得库存台账分页")
    @PreAuthorize("@ss.hasPermission('rd:stock-lot:query')")
    public CommonResult<PageResult<RdStockLotRespVO>> getStockLotPage(@Valid RdStockLotPageReqVO pageReqVO) {
        PageResult<RdStockLotDO> pageResult = stockLotService.getStockLotPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, RdStockLotRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出库存台账 Excel")
    @PreAuthorize("@ss.hasPermission('rd:stock-lot:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportStockLotExcel(@Valid RdStockLotPageReqVO pageReqVO,
                                    HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<RdStockLotDO> list = stockLotService.getStockLotPage(pageReqVO).getList();
        ExcelUtils.write(response, "库存台账.xls", "数据", RdStockLotRespVO.class,
                BeanUtils.toBean(list, RdStockLotRespVO.class));
    }

}
