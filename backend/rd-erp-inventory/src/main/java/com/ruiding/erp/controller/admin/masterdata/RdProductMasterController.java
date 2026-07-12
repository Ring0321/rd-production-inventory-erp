package com.ruiding.erp.controller.admin.masterdata;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.masterdata.vo.RdMasterDataPageReqVO;
import com.ruiding.erp.controller.admin.masterdata.vo.RdProductMasterSaveReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdProductMasterDO;
import com.ruiding.erp.service.masterdata.RdProductMasterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name = "RD ERP - 产品资料")
@RestController @RequestMapping("/rd/product-master")
public class RdProductMasterController {
    @Resource private RdProductMasterService service;
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('rd:product-master:create')")
    public CommonResult<Long> create(@Valid @RequestBody RdProductMasterSaveReqVO reqVO) { return success(service.create(reqVO)); }
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('rd:product-master:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody RdProductMasterSaveReqVO reqVO) { service.update(reqVO); return success(true); }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('rd:product-master:query')")
    public CommonResult<PageResult<RdProductMasterDO>> page(@Valid RdMasterDataPageReqVO reqVO) { return success(service.page(reqVO)); }
    @GetMapping("/options") @PreAuthorize("@ss.hasPermission('rd:product-master:query')")
    public CommonResult<List<RdProductMasterDO>> options(@RequestParam(required = false) String keyword) { return success(service.options(keyword)); }
}
