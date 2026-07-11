package com.ruiding.erp.controller.admin.masterdata;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.service.masterdata.RdCustomerProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "RD customer and product options")
@RestController
@RequestMapping("/rd/customer-product")
@Validated
public class RdCustomerProductController {

    @Resource
    private RdCustomerProductService service;

    @GetMapping("/customer-options")
    @Operation(summary = "Get searchable customer options")
    @PreAuthorize("@ss.hasPermission('rd:customer-product:query')")
    public CommonResult<List<String>> getCustomerOptions(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return success(service.getCustomerOptions(keyword, limit));
    }

    @GetMapping("/model-options")
    @Operation(summary = "Get customer-scoped product model options")
    @PreAuthorize("@ss.hasPermission('rd:customer-product:query')")
    public CommonResult<List<String>> getModelOptions(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return success(service.getModelOptions(customerName, keyword, limit));
    }
}
