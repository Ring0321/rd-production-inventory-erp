package com.ruiding.erp.service.order;

import com.ruiding.erp.controller.admin.order.vo.RdSaleOrderSaveReqVO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.mysql.order.RdOrderFulfillmentMapper;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.service.masterdata.RdCustomerProductPair;
import com.ruiding.erp.service.masterdata.RdCustomerProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RdSaleOrderOutputQuantityTest {

    @InjectMocks private RdSaleOrderServiceImpl service;
    @Mock private RdSaleOrderMapper saleOrderMapper;
    @Mock private RdOrderFulfillmentMapper fulfillmentMapper;
    @Mock private RdCustomerProductService customerProductService;

    @Test
    void legacyUpdateKeepsManualTwoWhenActualNineAlreadyIncludesSplitSeven() {
        RdSaleOrderDO existing = new RdSaleOrderDO().setId(1L).setOrderNo("SO-1")
                .setRequiredQty(new BigDecimal("20"))
                .setManualOutputQty(new BigDecimal("2"))
                .setSplitOutputQty(new BigDecimal("7"))
                .setActualOutputQty(new BigDecimal("9"));
        when(saleOrderMapper.selectById(1L)).thenReturn(existing);
        when(saleOrderMapper.selectByOrderNo("SO-1")).thenReturn(existing);
        when(customerProductService.recordPair("Customer", "Model"))
                .thenReturn(new RdCustomerProductPair("Customer", "Model"));

        service.updateSaleOrder(new RdSaleOrderSaveReqVO().setId(1L).setOrderNo("SO-1")
                .setOrderDate(LocalDate.of(2026, 7, 12)).setCustomerName("Customer").setProductModel("Model")
                .setRequiredQty(new BigDecimal("20")).setActualOutputQty(new BigDecimal("9")));

        ArgumentCaptor<RdSaleOrderDO> captor = ArgumentCaptor.forClass(RdSaleOrderDO.class);
        verify(saleOrderMapper).updateById(captor.capture());
        assertEquals(0, new BigDecimal("2").compareTo(captor.getValue().getManualOutputQty()));
        assertEquals(0, new BigDecimal("7").compareTo(captor.getValue().getSplitOutputQty()));
        assertEquals(0, new BigDecimal("9").compareTo(captor.getValue().getActualOutputQty()));
    }
}
