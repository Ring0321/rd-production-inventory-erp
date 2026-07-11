package com.ruiding.erp.dal.mysql.transfer;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.ruiding.erp.controller.admin.transfer.vo.RdTransferOrderPageReqVO;
import com.ruiding.erp.dal.dataobject.transfer.RdTransferOrderDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * RD transfer order Mapper.
 */
@Mapper
public interface RdTransferOrderMapper extends BaseMapperX<RdTransferOrderDO> {

    @Select("SELECT * FROM rd_transfer_order WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    RdTransferOrderDO selectByIdForUpdate(Long id);

    default RdTransferOrderDO selectByTransferNo(String transferNo) {
        return selectOne(RdTransferOrderDO::getTransferNo, transferNo);
    }

    default PageResult<RdTransferOrderDO> selectPage(RdTransferOrderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RdTransferOrderDO>()
                .likeIfPresent(RdTransferOrderDO::getTransferNo, reqVO.getTransferNo())
                .eqIfPresent(RdTransferOrderDO::getFromOrderId, reqVO.getFromOrderId())
                .eqIfPresent(RdTransferOrderDO::getToOrderId, reqVO.getToOrderId())
                .eqIfPresent(RdTransferOrderDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(RdTransferOrderDO::getTransferDate, reqVO.getTransferDate())
                .orderByDesc(RdTransferOrderDO::getTransferDate)
                .orderByDesc(RdTransferOrderDO::getId));
    }

}
