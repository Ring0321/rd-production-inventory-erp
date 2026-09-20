package com.ruiding.erp.service.masterdata;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.masterdata.vo.RdMasterDataPageReqVO;
import com.ruiding.erp.controller.admin.masterdata.vo.RdWarehouseLocationSaveReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.WAREHOUSE_LOCATION_NOT_EXISTS;
@Service
public class RdWarehouseLocationServiceImpl implements RdWarehouseLocationService {
    @Resource private RdWarehouseLocationMapper mapper;
    @Override public Long create(RdWarehouseLocationSaveReqVO reqVO) {
        RdWarehouseLocationDO value = BeanUtils.toBean(reqVO, RdWarehouseLocationDO.class);
        if (value.getEnabled() == null) value.setEnabled(true);
        if (value.getFrozen() == null) value.setFrozen(false);
        if (value.getLocationStatus() == null) value.setLocationStatus("AVAILABLE");
        mapper.insert(value); return value.getId();
    }
    @Override public void update(RdWarehouseLocationSaveReqVO reqVO) {
        if (reqVO.getId() == null || mapper.selectById(reqVO.getId()) == null) throw exception(WAREHOUSE_LOCATION_NOT_EXISTS);
        mapper.updateById(BeanUtils.toBean(reqVO, RdWarehouseLocationDO.class));
    }
    @Override public PageResult<RdWarehouseLocationDO> page(RdMasterDataPageReqVO reqVO) {
        return mapper.selectPage(reqVO, query(reqVO.getKeyword(), reqVO.getEnabled()));
    }
    @Override public List<RdWarehouseLocationDO> options(String keyword) {
        return mapper.selectList(query(keyword, true).eq(RdWarehouseLocationDO::getFrozen, false).last("LIMIT 100"));
    }
    private LambdaQueryWrapperX<RdWarehouseLocationDO> query(String keyword, Boolean enabled) {
        LambdaQueryWrapperX<RdWarehouseLocationDO> q = new LambdaQueryWrapperX<>();
        if (keyword != null && !keyword.isBlank()) q.and(w -> w.like(RdWarehouseLocationDO::getLocationCode, keyword)
                .or().like(RdWarehouseLocationDO::getWarehouseName, keyword).or().like(RdWarehouseLocationDO::getAreaName, keyword));
        q.eqIfPresent(RdWarehouseLocationDO::getEnabled, enabled);
        q.orderByAsc(RdWarehouseLocationDO::getLocationCode);
        return q;
    }
}
