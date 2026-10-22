package com.ruiding.erp.controller.admin.workassistant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.service.workassistant.RdWorkQueryContract;
import com.ruiding.erp.service.workassistant.RdWorkQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/rd/work-assistant")
public class RdWorkAssistantController {
    @Resource private RdWorkQueryService service;

    @GetMapping("/query")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<RdWorkQueryContract.Result<?>> query(@RequestParam MultiValueMap<String, String> parameters) {
        return success(service.query(RdWorkQueryContract.Request.parse(parameters)));
    }
}
