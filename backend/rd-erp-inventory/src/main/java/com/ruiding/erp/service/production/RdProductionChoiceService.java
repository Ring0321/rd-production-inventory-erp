package com.ruiding.erp.service.production;

import com.ruiding.erp.controller.admin.production.vo.RdProductionLayerOptionRespVO;
import java.util.List;

public interface RdProductionChoiceService {
    List<String> furnaces(String customerName, String productModel, String keyword, Integer limit);
    List<RdProductionLayerOptionRespVO> layers(String customerName, String productModel, String keyword, Integer limit);
}
