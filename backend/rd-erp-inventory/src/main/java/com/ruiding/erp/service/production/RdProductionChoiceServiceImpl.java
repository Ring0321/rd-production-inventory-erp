package com.ruiding.erp.service.production;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.controller.admin.production.vo.RdProductionLayerOptionRespVO;
import com.ruiding.erp.dal.mysql.production.RdProductionChoiceMapper;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class RdProductionChoiceServiceImpl implements RdProductionChoiceService {
    @Resource private RdProductionChoiceMapper mapper;
    private static int limit(Integer value) { return value == null ? 50 : Math.max(1, Math.min(100, value)); }

    @Override
    public List<String> furnaces(String customerName, String productModel, String keyword, Integer count) {
        return mapper.selectFurnaces(TenantContextHolder.getRequiredTenantId(), StrUtil.trim(customerName),
                StrUtil.trim(productModel), StrUtil.trim(keyword), limit(count));
    }

    @Override
    public List<RdProductionLayerOptionRespVO> layers(String customerName, String productModel,
                                                    String keyword, Integer count) {
        int size = limit(count);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LinkedHashMap<String, RdProductionLayerOptionRespVO> result = new LinkedHashMap<>();
        for (RdProductionLayerOptionRespVO option : mapper.selectEnabledLayers(tenantId, StrUtil.trim(keyword), size)) {
            result.putIfAbsent(option.getValue(), option);
        }
        for (RdProductionLayerOptionRespVO option : mapper.selectHistoryLayers(tenantId, StrUtil.trim(customerName),
                StrUtil.trim(productModel), StrUtil.trim(keyword), size)) {
            result.putIfAbsent(option.getValue(), option);
        }
        return result.values().stream().limit(size).toList();
    }
}
