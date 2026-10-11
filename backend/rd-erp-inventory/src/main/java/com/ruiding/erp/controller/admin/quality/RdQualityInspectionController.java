package com.ruiding.erp.controller.admin.quality;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionCreateReqVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityReviewReqVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionDetailRespVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionBatchCreateReqVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionScopeReqVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionScopeRespVO;
import com.ruiding.erp.controller.admin.quality.vo.RdQualityInspectionPageReqVO;
import com.ruiding.erp.dal.dataobject.quality.RdQualityInspectionDO;
import com.ruiding.erp.service.quality.RdQualityInspectionService;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
@RestController @RequestMapping("/rd/quality-inspection")
public class RdQualityInspectionController {
    @Resource private RdQualityInspectionService service;
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:create')")
    public CommonResult<Long> create(@Valid @RequestBody RdQualityInspectionCreateReqVO reqVO) {
        return success(service.create(reqVO.getPackageId(), reqVO.getItems(), reqVO.getRemark()));
    }
    @GetMapping("/scope") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:query')")
    public CommonResult<RdQualityInspectionScopeRespVO> scope(@Valid RdQualityInspectionScopeReqVO reqVO) {
        return success(service.scope(reqVO));
    }
    @PostMapping("/create-batch") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:create')")
    public CommonResult<Long> createBatch(@Valid @RequestBody RdQualityInspectionBatchCreateReqVO reqVO) {
        return success(service.createBatch(reqVO));
    }
    @PostMapping("/submit") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:submit')")
    public CommonResult<Boolean> submit(@RequestParam Long id) { service.submit(id, getLoginUserId(), getLoginUserNickname()); return success(true); }
    @PostMapping("/approve") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:approve')")
    public CommonResult<Boolean> approve(@RequestParam Long id) { service.approve(id, getLoginUserId(), getLoginUserNickname()); return success(true); }
    @PostMapping("/reject") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:approve')")
    public CommonResult<Boolean> reject(@Valid @RequestBody RdQualityReviewReqVO reqVO) {
        service.reject(reqVO.getId(), getLoginUserId(), getLoginUserNickname(), reqVO.getReason()); return success(true);
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:query')")
    public CommonResult<RdQualityInspectionDetailRespVO> get(@RequestParam Long id) {
        return success(service.getDetail(id));
    }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('rd:quality-inspection:query')")
    public CommonResult<PageResult<RdQualityInspectionDO>> page(@Valid RdQualityInspectionPageReqVO pageParam) { return success(service.page(pageParam)); }
}
