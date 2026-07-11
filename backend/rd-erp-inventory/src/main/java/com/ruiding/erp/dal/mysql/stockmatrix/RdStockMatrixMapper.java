package com.ruiding.erp.dal.mysql.stockmatrix;

import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageReqVO;
import com.ruiding.erp.dal.dataobject.stockmatrix.RdStockMatrixAggregateDO;
import com.ruiding.erp.dal.dataobject.stockmatrix.RdStockMatrixDailyDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RdStockMatrixMapper {
    Long selectMatrixCount(@Param("tenantId") Long tenantId,
                           @Param("req") RdStockMatrixPageReqVO req);

    List<RdStockMatrixAggregateDO> selectMatrixPage(@Param("tenantId") Long tenantId,
                                                    @Param("req") RdStockMatrixPageReqVO req,
                                                    @Param("offset") long offset,
                                                    @Param("limit") int limit);

    RdStockMatrixAggregateDO selectMatrixTotals(@Param("tenantId") Long tenantId,
                                                @Param("req") RdStockMatrixPageReqVO req);

    List<RdStockMatrixDailyDO> selectDailyMovements(@Param("tenantId") Long tenantId,
                                                    @Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate,
                                                    @Param("stockLotIds") List<Long> stockLotIds);

    List<RdStockMatrixDailyDO> selectDailyTotals(@Param("tenantId") Long tenantId,
                                                 @Param("req") RdStockMatrixPageReqVO req);
}
