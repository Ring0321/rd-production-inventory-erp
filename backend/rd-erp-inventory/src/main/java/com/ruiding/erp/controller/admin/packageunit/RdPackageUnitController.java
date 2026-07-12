package com.ruiding.erp.controller.admin.packageunit;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageGenerateReqVO;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackagePrintReqVO;
import com.ruiding.erp.dal.dataobject.packageunit.RdPackageUnitDO;
import com.ruiding.erp.service.packageunit.RdPackageUnitService;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
@RestController @RequestMapping("/rd/package-unit")
public class RdPackageUnitController {
    @Resource private RdPackageUnitService service;
    @PostMapping("/generate") @PreAuthorize("@ss.hasPermission('rd:package-unit:generate')")
    public CommonResult<List<RdPackageUnitDO>> generate(@Valid @RequestBody RdPackageGenerateReqVO reqVO) {
        return success(service.generate(reqVO.getProductionOutputId(), reqVO.getPackageCount()));
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<RdPackageUnitDO> get(@RequestParam Long id) { return success(service.get(id)); }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<PageResult<RdPackageUnitDO>> page(@Valid PageParam pageParam) { return success(service.page(pageParam)); }
    @GetMapping("/trace") @PreAuthorize("@ss.hasPermission('rd:package-unit:query')")
    public CommonResult<List<RdPackageUnitDO>> trace(
            @RequestParam(required = false) String packageCode,
            @RequestParam(required = false) Long productionOutputId) {
        return success(service.trace(packageCode, productionOutputId));
    }
    @PostMapping("/print") @PreAuthorize("@ss.hasPermission('rd:label-print:print')")
    public CommonResult<Boolean> print(@Valid @RequestBody RdPackagePrintReqVO reqVO) {
        service.markPrinted(reqVO.getPackageId(), getLoginUserId(), getLoginUserNickname(), null); return success(true);
    }
    @PostMapping("/reprint") @PreAuthorize("@ss.hasPermission('rd:label-print:reprint')")
    public CommonResult<Boolean> reprint(@Valid @RequestBody RdPackagePrintReqVO reqVO) {
        service.markPrinted(reqVO.getPackageId(), getLoginUserId(), getLoginUserNickname(), reqVO.getReprintReason()); return success(true);
    }
}
