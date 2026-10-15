package com.ruiding.erp.controller.admin.weight;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.controller.admin.weight.vo.RdWeightSuggestReqVO;
import com.ruiding.erp.controller.admin.weight.vo.RdWeightSuggestRespVO;
import com.ruiding.erp.service.weight.RdWeightAssistantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "RD ERP - 只读配吨建议")
@RestController
@RequestMapping("/rd/weight-assistant")
public class RdWeightAssistantController {
    @Resource private RdWeightAssistantService service;

    @PostMapping("/suggest")
    @PreAuthorize("@ss.hasPermission('rd:pickup-slip:query') and @ss.hasPermission('rd:package-unit:query')"
            + " and @ss.hasPermission('rd:loading-task:dispatch')")
    public CommonResult<RdWeightSuggestRespVO> suggest(@Valid @RequestBody RdWeightSuggestReqVO req) {
        return success(service.suggest(req, getLoginUserId()));
    }
}
