package com.ruiding.erp.service.ordersplit;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitDetailRespVO;
import com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitPageReqVO;
import com.ruiding.erp.dal.dataobject.order.RdSaleOrderDO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitOutputBindDO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultDO;
import com.ruiding.erp.dal.dataobject.ordersplit.RdOrderSplitResultLineDO;
import com.ruiding.erp.dal.dataobject.production.RdProductionOutputDO;
import com.ruiding.erp.dal.mysql.order.RdSaleOrderMapper;
import com.ruiding.erp.dal.mysql.ordersplit.RdOrderSplitOutputBindMapper;
import com.ruiding.erp.dal.mysql.ordersplit.RdOrderSplitResultLineMapper;
import com.ruiding.erp.dal.mysql.ordersplit.RdOrderSplitResultMapper;
import com.ruiding.erp.dal.mysql.production.RdProductionOutputMapper;
import com.ruiding.erp.enums.RdOrderStatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class RdOrderSplitServiceImplTest {

    @InjectMocks
    private RdOrderSplitServiceImpl service;
    @Mock
    private RdOrderSplitOutputBindMapper bindMapper;
    @Mock
    private RdSaleOrderMapper saleOrderMapper;
    @Mock private RdOrderSplitResultMapper resultMapper;
    @Mock private RdOrderSplitResultLineMapper resultLineMapper;
    @Mock private RdProductionOutputMapper productionOutputMapper;

    @Test
    void shouldRecomputeOrderOutputFromAllActiveBindings() {
        when(saleOrderMapper.selectByIdForUpdate(7L)).thenReturn(new RdSaleOrderDO().setId(7L)
                .setRequiredQty(new BigDecimal("20")).setManualOutputQty(new BigDecimal("2"))
                .setAllocatedQty(new BigDecimal("3")).setTransferInQty(new BigDecimal("1"))
                .setTransferOutQty(new BigDecimal("2")));
        when(bindMapper.selectActiveByOrderId(7L)).thenReturn(List.of(
                new RdOrderSplitOutputBindDO().setOutputQtySnapshot(new BigDecimal("3")),
                new RdOrderSplitOutputBindDO().setOutputQtySnapshot(new BigDecimal("4"))));

        service.recomputeSaleOrderOutput(7L);

        verify(saleOrderMapper).updateById(org.mockito.ArgumentMatchers.<RdSaleOrderDO>argThat(order ->
                new BigDecimal("7").compareTo(order.getSplitOutputQty()) == 0
                        && new BigDecimal("9").compareTo(order.getActualOutputQty()) == 0
                        && new BigDecimal("11").compareTo(order.getDifferenceQty()) == 0
                        && new BigDecimal("11").compareTo(order.getFulfilledQty()) == 0
                        && new BigDecimal("9").compareTo(order.getAdjustedDifferenceQty()) == 0
                        && RdOrderStatusEnum.PARTIAL.getStatus().equals(order.getStatus())));
    }

    @Test
    void shouldMarkOrderFulfilledWhenBindingSatisfiesRequiredQuantity() {
        when(saleOrderMapper.selectByIdForUpdate(7L)).thenReturn(new RdSaleOrderDO().setId(7L)
                .setRequiredQty(new BigDecimal("10")).setManualOutputQty(BigDecimal.ZERO));
        when(bindMapper.selectActiveByOrderId(7L)).thenReturn(List.of(
                new RdOrderSplitOutputBindDO().setOutputQtySnapshot(new BigDecimal("10"))));

        service.recomputeSaleOrderOutput(7L);

        verify(saleOrderMapper).updateById(org.mockito.ArgumentMatchers.<RdSaleOrderDO>argThat(order ->
                new BigDecimal("10").compareTo(order.getFulfilledQty()) == 0
                        && BigDecimal.ZERO.compareTo(order.getAdjustedDifferenceQty()) == 0
                        && RdOrderStatusEnum.FULFILLED.getStatus().equals(order.getStatus())));
    }

    @Test
    void shouldRejectStaleResultVersionOnConfirm() {
        when(resultMapper.selectByIdForUpdate(3L)).thenReturn(new RdOrderSplitResultDO().setId(3L)
                .setStatus("DRAFT").setVersion(4));
        com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitActionReqVO req =
                new com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitActionReqVO().setId(3L).setVersion(3);
        assertThrows(RuntimeException.class, () -> service.confirm(req, 1L, "operator"));
    }

    @Test
    void shouldRejectBindingWhenFurnaceLayerDoesNotMatch() {
        when(resultMapper.selectByIdForUpdate(3L)).thenReturn(new RdOrderSplitResultDO().setId(3L)
                .setSaleOrderId(7L).setStatus("CONFIRMED").setVersion(1)
                .setCustomerNameSnapshot("A").setProductModelSnapshot("M"));
        when(resultLineMapper.selectByIdForUpdate(5L)).thenReturn(new RdOrderSplitResultLineDO().setId(5L)
                .setResultId(3L).setFurnaceLayer("L1"));
        when(productionOutputMapper.selectByIdForUpdate(9L)).thenReturn(new RdProductionOutputDO().setId(9L)
                .setStatus("CONFIRMED").setCustomerName("A").setProductModel("M").setFurnaceLayer("L2"));
        com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitBindReqVO req =
                new com.ruiding.erp.controller.admin.ordersplit.vo.RdOrderSplitBindReqVO()
                        .setId(3L).setVersion(1).setResultLineId(5L).setProductionOutputId(9L);
        assertThrows(RuntimeException.class, () -> service.bindOutput(req, 1L, "operator"));
    }

    @Test
    void shouldPopulatePageBoundQuantityWithOneBatchAggregation() {
        RdOrderSplitResultDO first = new RdOrderSplitResultDO().setId(1L);
        RdOrderSplitResultDO second = new RdOrderSplitResultDO().setId(2L);
        RdOrderSplitPageReqVO reqVO = new RdOrderSplitPageReqVO();
        reqVO.setSaleOrderNo("SO-12");
        when(resultMapper.selectPage(reqVO)).thenReturn(new PageResult<>(List.of(first, second), 2L));
        when(bindMapper.selectActiveQtyByResultIds(List.of(1L, 2L))).thenReturn(List.of(
                new RdOrderSplitOutputBindDO().setResultId(1L).setOutputQtySnapshot(new BigDecimal("7"))));

        PageResult<RdOrderSplitResultDO> page = service.getPage(reqVO);

        assertEquals(0, new BigDecimal("7").compareTo(page.getList().get(0).getActualBoundOutputQty()));
        assertEquals(0, BigDecimal.ZERO.compareTo(page.getList().get(1).getActualBoundOutputQty()));
        verify(bindMapper).selectActiveQtyByResultIds(List.of(1L, 2L));
    }

    @Test
    void shouldEnrichBindingDetailWithFurnaceNumberInOneProductionQuery() {
        when(resultMapper.selectById(3L)).thenReturn(new RdOrderSplitResultDO().setId(3L));
        when(resultLineMapper.selectByResultId(3L)).thenReturn(List.of(
                new RdOrderSplitResultLineDO().setActualBoundQty(new BigDecimal("4"))));
        when(bindMapper.selectByResultId(3L)).thenReturn(List.of(
                new RdOrderSplitOutputBindDO().setId(8L).setResultId(3L).setProductionOutputId(9L)));
        when(productionOutputMapper.selectByIds(java.util.Set.of(9L))).thenReturn(List.of(
                new RdProductionOutputDO().setId(9L).setFurnaceNo("F-009").setFurnaceLayer("L1")));

        RdOrderSplitDetailRespVO detail = service.getDetail(3L);

        assertEquals("F-009", detail.getBindings().get(0).getFurnaceNo());
        assertEquals(0, new BigDecimal("4").compareTo(detail.getResult().getActualBoundOutputQty()));
        verify(productionOutputMapper).selectByIds(java.util.Set.of(9L));
    }
}
