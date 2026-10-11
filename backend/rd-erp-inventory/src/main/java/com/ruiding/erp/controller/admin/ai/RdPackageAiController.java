package com.ruiding.erp.controller.admin.ai;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.controller.admin.ai.vo.*;
import com.ruiding.erp.service.ai.RdPackageAiService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController @Validated
@RequestMapping("/rd/ai/package")
public class RdPackageAiController {
    @Resource private RdPackageAiService service;
    @GetMapping("/context") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<RdPackageAiContext> context(@RequestParam @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}") String packageCode) {
        return success(service.context(packageCode, getLoginUserId()));
    }
    @PostMapping("/ask") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<RdPackageAiAnswer> ask(@Valid @RequestBody RdPackageAiAskReqVO request) {
        return success(service.ask(request, getLoginUserId()));
    }
    @GetMapping("/result") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<RdPackageAiAnswer> result(@Valid @ModelAttribute RdPackageAiAskReqVO request) {
        return success(service.result(request, getLoginUserId()));
    }
}
