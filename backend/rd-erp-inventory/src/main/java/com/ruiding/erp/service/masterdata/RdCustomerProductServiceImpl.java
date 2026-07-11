package com.ruiding.erp.service.masterdata;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.ruiding.erp.dal.mysql.masterdata.RdCustomerProductMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class RdCustomerProductServiceImpl implements RdCustomerProductService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    @Resource
    private RdCustomerProductMapper mapper;

    @Override
    public RdCustomerProductPair recordPair(String customerName, String productModel) {
        String normalizedCustomer = normalizeRequired(customerName, "customerName");
        String normalizedModel = normalizeRequired(productModel, "productModel");
        mapper.upsertPair(TenantContextHolder.getRequiredTenantId(), normalizedCustomer, normalizedModel);
        return new RdCustomerProductPair(normalizedCustomer, normalizedModel);
    }

    @Override
    public List<String> getCustomerOptions(String keyword, Integer limit) {
        return mapper.selectCustomerOptions(TenantContextHolder.getRequiredTenantId(),
                StrUtil.trim(keyword), normalizeLimit(limit));
    }

    @Override
    public List<String> getModelOptions(String customerName, String keyword, Integer limit) {
        return mapper.selectModelOptions(TenantContextHolder.getRequiredTenantId(),
                StrUtil.trim(customerName), StrUtil.trim(keyword), normalizeLimit(limit));
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = StrUtil.trim(value);
        if (StrUtil.isEmpty(normalized)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static int normalizeLimit(Integer limit) {
        return limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
