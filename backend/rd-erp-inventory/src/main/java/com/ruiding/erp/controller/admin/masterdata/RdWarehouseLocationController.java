package com.ruiding.erp.controller.admin.masterdata;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.masterdata.vo.RdMasterDataPageReqVO;
import com.ruiding.erp.controller.admin.masterdata.vo.RdWarehouseLocationSaveReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.service.masterdata.RdWarehouseLocationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@Tag(name = "RD ERP - 仓库货位")
@RestController @RequestMapping("/rd/warehouse-location")
public class RdWarehouseLocationController {
    @Resource private RdWarehouseLocationService service;
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('rd:warehouse-location:create')")
    public CommonResult<Long> create(@Valid @RequestBody RdWarehouseLocationSaveReqVO reqVO) { return success(service.create(reqVO)); }
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('rd:warehouse-location:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody RdWarehouseLocationSaveReqVO reqVO) { service.update(reqVO); return success(true); }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('rd:warehouse-location:query')")
    public CommonResult<PageResult<RdWarehouseLocationDO>> page(@Valid RdMasterDataPageReqVO reqVO) { return success(service.page(reqVO)); }
    @GetMapping("/options") @PreAuthorize("@ss.hasPermission('rd:warehouse-location:query')")
    public CommonResult<List<RdWarehouseLocationDO>> options(@RequestParam(required = false) String keyword) { return success(service.options(keyword)); }
}
