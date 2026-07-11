package com.ruiding.erp.service.masterdata;

import java.util.List;

public interface RdCustomerProductService {

    RdCustomerProductPair recordPair(String customerName, String productModel);

    List<String> getCustomerOptions(String keyword, Integer limit);

    List<String> getModelOptions(String customerName, String keyword, Integer limit);
}
