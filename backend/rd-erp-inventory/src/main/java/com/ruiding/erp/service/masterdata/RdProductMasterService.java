package com.ruiding.erp.service.masterdata;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.masterdata.vo.RdMasterDataPageReqVO;
import com.ruiding.erp.controller.admin.masterdata.vo.RdProductMasterSaveReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdProductMasterDO;
import java.util.List;
public interface RdProductMasterService {
    Long create(RdProductMasterSaveReqVO reqVO);
    void update(RdProductMasterSaveReqVO reqVO);
    PageResult<RdProductMasterDO> page(RdMasterDataPageReqVO reqVO);
    List<RdProductMasterDO> options(String keyword);
}
