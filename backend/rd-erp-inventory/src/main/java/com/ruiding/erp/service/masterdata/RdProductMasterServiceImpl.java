package com.ruiding.erp.service.masterdata;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.masterdata.vo.RdMasterDataPageReqVO;
import com.ruiding.erp.controller.admin.masterdata.vo.RdProductMasterSaveReqVO;
import com.ruiding.erp.dal.dataobject.masterdata.RdProductMasterDO;
import com.ruiding.erp.dal.mysql.masterdata.RdProductMasterMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.PRODUCT_MASTER_NOT_EXISTS;
@Service
public class RdProductMasterServiceImpl implements RdProductMasterService {
    @Resource private RdProductMasterMapper mapper;
    @Override public Long create(RdProductMasterSaveReqVO reqVO) {
        RdProductMasterDO value = BeanUtils.toBean(reqVO, RdProductMasterDO.class);
        if (value.getEnabled() == null) value.setEnabled(true);
        mapper.insert(value); return value.getId();
    }
    @Override public void update(RdProductMasterSaveReqVO reqVO) {
        if (reqVO.getId() == null || mapper.selectById(reqVO.getId()) == null) throw exception(PRODUCT_MASTER_NOT_EXISTS);
        mapper.updateById(BeanUtils.toBean(reqVO, RdProductMasterDO.class));
    }
    @Override public PageResult<RdProductMasterDO> page(RdMasterDataPageReqVO reqVO) {
        return mapper.selectPage(reqVO, query(reqVO.getKeyword(), reqVO.getEnabled()));
    }
    @Override public List<RdProductMasterDO> options(String keyword) {
        return mapper.selectList(query(keyword, true).last("LIMIT 100"));
    }
    private LambdaQueryWrapperX<RdProductMasterDO> query(String keyword, Boolean enabled) {
        LambdaQueryWrapperX<RdProductMasterDO> q = new LambdaQueryWrapperX<>();
        if (keyword != null && !keyword.isBlank()) q.and(w -> w.like(RdProductMasterDO::getProductCode, keyword)
                .or().like(RdProductMasterDO::getProductName, keyword).or().like(RdProductMasterDO::getProductModel, keyword));
        q.eqIfPresent(RdProductMasterDO::getEnabled, enabled);
        q.orderByAsc(RdProductMasterDO::getProductCode);
        return q;
    }
}
