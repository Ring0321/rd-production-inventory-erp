package com.ruiding.erp.controller.admin.packageunit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageRelationsRespVO;
import com.ruiding.erp.service.packageunit.RdPackageRelationsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@RestController
@RequestMapping("/rd/package-unit")
public class RdPackageRelationsController {
    @Resource private RdPackageRelationsService service;

    @GetMapping("/relations")
    @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<RdPackageRelationsRespVO> getRelations(
            @RequestParam("packageCode") String packageCode, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return success(service.getRelations(packageCode, getLoginUserId()));
    }
}
