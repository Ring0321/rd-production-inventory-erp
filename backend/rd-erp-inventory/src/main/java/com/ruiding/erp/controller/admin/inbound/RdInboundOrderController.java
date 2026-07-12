package com.ruiding.erp.controller.admin.inbound;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.inbound.vo.RdInboundOrderCreateReqVO;
import com.ruiding.erp.controller.admin.inbound.vo.RdInboundReverseReqVO;
import com.ruiding.erp.controller.admin.inbound.vo.RdInboundOrderDetailRespVO;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderDO;
import com.ruiding.erp.dal.mysql.inbound.RdInboundOrderLineMapper;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import com.ruiding.erp.service.inbound.RdInboundOrderService;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserNickname;
@RestController @RequestMapping("/rd/inbound-order")
public class RdInboundOrderController {
    @Resource private RdInboundOrderService service;
    @Resource private RdInboundOrderLineMapper lineMapper;
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('rd:inbound-order:create')")
    public CommonResult<Long> create(@Valid @RequestBody RdInboundOrderCreateReqVO reqVO) {
        return success(service.create(reqVO.getBizDate(), reqVO.getProductionOutputId(), reqVO.getLines(), reqVO.getRemark()));
    }
    @PostMapping("/submit") @PreAuthorize("@ss.hasPermission('rd:inbound-order:submit')")
    public CommonResult<Boolean> submit(@RequestParam Long id) { service.submit(id, getLoginUserId(), getLoginUserNickname()); return success(true); }
    @PostMapping("/verify") @PreAuthorize("@ss.hasPermission('rd:inbound-order:verify')")
    public CommonResult<Boolean> verify(@RequestParam Long id) { service.verify(id, getLoginUserId(), getLoginUserNickname()); return success(true); }
    @PostMapping("/post") @PreAuthorize("@ss.hasPermission('rd:inbound-order:post')")
    public CommonResult<Boolean> post(@RequestParam Long id) { service.post(id, getLoginUserId(), getLoginUserNickname()); return success(true); }
    @PostMapping("/reverse") @PreAuthorize("@ss.hasPermission('rd:inbound-order:reverse')")
    public CommonResult<Boolean> reverse(@Valid @RequestBody RdInboundReverseReqVO reqVO) {
        service.reverse(reqVO.getId(), getLoginUserId(), getLoginUserNickname(), reqVO.getReason()); return success(true);
    }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('rd:inbound-order:query')")
    public CommonResult<RdInboundOrderDetailRespVO> get(@RequestParam Long id) {
        RdInboundOrderDO order = service.get(id);
        RdInboundOrderDetailRespVO detail = BeanUtils.toBean(order, RdInboundOrderDetailRespVO.class);
        detail.setLines(lineMapper.selectListByOrderId(id));
        return success(detail);
    }
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('rd:inbound-order:query')")
    public CommonResult<PageResult<RdInboundOrderDO>> page(@Valid PageParam pageParam) { return success(service.page(pageParam)); }
}
