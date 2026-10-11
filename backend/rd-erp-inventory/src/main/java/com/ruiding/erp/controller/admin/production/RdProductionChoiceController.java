package com.ruiding.erp.controller.admin.production;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.controller.admin.production.vo.RdProductionLayerOptionRespVO;
import com.ruiding.erp.service.production.RdProductionChoiceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/rd/production-output")
public class RdProductionChoiceController {
    @Resource private RdProductionChoiceService service;
    @GetMapping("/furnace-options") @PreAuthorize("@ss.hasPermission('rd:production-output:query')")
    public CommonResult<List<String>> furnaces(@RequestParam(required=false) String customerName,
                                               @RequestParam(required=false) String productModel,
                                               @RequestParam(required=false) String keyword,
                                               @RequestParam(required=false) Integer limit) {
        return success(service.furnaces(customerName, productModel, keyword, limit));
    }
    @GetMapping("/layer-options") @PreAuthorize("@ss.hasPermission('rd:production-output:query')")
    public CommonResult<List<RdProductionLayerOptionRespVO>> layers(@RequestParam(required=false) String customerName,
                                                                  @RequestParam(required=false) String productModel,
                                                                  @RequestParam(required=false) String keyword,
                                                                  @RequestParam(required=false) Integer limit) {
        return success(service.layers(customerName, productModel, keyword, limit));
    }
}
