package com.ruiding.erp.service.masterdata;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.masterdata.vo.RdMasterDataPageReqVO;
import com.ruiding.erp.controller.admin.masterdata.vo.RdWarehouseLocationSaveReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import java.util.List;
public interface RdWarehouseLocationService {
    Long create(RdWarehouseLocationSaveReqVO reqVO);
    void update(RdWarehouseLocationSaveReqVO reqVO);
    PageResult<RdWarehouseLocationDO> page(RdMasterDataPageReqVO reqVO);
    List<RdWarehouseLocationDO> options(String keyword);
}
