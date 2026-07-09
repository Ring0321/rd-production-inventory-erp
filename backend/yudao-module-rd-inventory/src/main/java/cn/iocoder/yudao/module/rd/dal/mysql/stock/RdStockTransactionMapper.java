package cn.iocoder.yudao.module.rd.dal.mysql.stock;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.rd.dal.dataobject.stock.RdStockTransactionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * RD stock transaction Mapper.
 */
@Mapper
public interface RdStockTransactionMapper extends BaseMapperX<RdStockTransactionDO> {
}
