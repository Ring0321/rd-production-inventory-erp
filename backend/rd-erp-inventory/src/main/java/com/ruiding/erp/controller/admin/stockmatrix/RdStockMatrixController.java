package com.ruiding.erp.controller.admin.stockmatrix;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageReqVO;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageRespVO;
import com.ruiding.erp.service.stockmatrix.RdStockMatrixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "RD stock movement matrix")
@RestController
@RequestMapping("/rd/stock-matrix")
@Validated
public class RdStockMatrixController {

    @Resource
    private RdStockMatrixService service;

    @GetMapping("/page")
    @Operation(summary = "Get stock movement matrix page")
    @PreAuthorize("@ss.hasPermission('rd:stock-matrix:query')")
    public CommonResult<RdStockMatrixPageRespVO> getMatrixPage(@Valid RdStockMatrixPageReqVO reqVO) {
        return success(service.getMatrixPage(reqVO));
    }
}
