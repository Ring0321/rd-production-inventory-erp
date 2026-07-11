package com.ruiding.erp.service.stockmatrix;

import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageReqVO;
import com.ruiding.erp.controller.admin.stockmatrix.vo.RdStockMatrixPageRespVO;

public interface RdStockMatrixService {
    RdStockMatrixPageRespVO getMatrixPage(RdStockMatrixPageReqVO reqVO);
}
