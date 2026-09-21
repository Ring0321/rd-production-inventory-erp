package com.ruiding.erp.enums;

/**
 * RD business operation-log constants.
 */
public interface RdLogRecordConstants {

    String BIZ_ID_VARIABLE = "rdBizId";
    String BIZ_ID_EXPRESSION = "{{#rdBizId}}";

    String PRODUCTION_TYPE = "RD 生产";
    String PRODUCTION_CREATE_SUB_TYPE = "新增生产产出";
    String PRODUCTION_CREATE_SUCCESS = "新增生产产出，炉号【{{#createReqVO.furnaceNo}}】，炉层【{{#createReqVO.furnaceLayer}}】，数量【{{#createReqVO.outputQty}}】";
    String PRODUCTION_UPDATE_SUB_TYPE = "修改生产产出";
    String PRODUCTION_UPDATE_SUCCESS = "修改生产产出【{{#updateReqVO.id}}】，炉号【{{#updateReqVO.furnaceNo}}】，数量【{{#updateReqVO.outputQty}}】";
    String PRODUCTION_CANCEL_SUB_TYPE = "作废生产产出";
    String PRODUCTION_CANCEL_SUCCESS = "作废生产产出【{{#id}}】";
    String PRODUCTION_INBOUND_SUB_TYPE = "生产产出入库";
    String PRODUCTION_INBOUND_SUCCESS = "生产产出【{{#id}}】转入库存";

    String STOCK_TYPE = "RD 库存";
    String STOCK_OPENING_SUB_TYPE = "库存期初录入";
    String STOCK_OPENING_SUCCESS = "录入期初库存，货位【{{#createReqVO.locationCode}}】，炉号【{{#createReqVO.furnaceNo}}】，数量【{{#createReqVO.quantity}}】，备注【{{#createReqVO.remark}}】";
    String STOCK_ADJUST_SUB_TYPE = "库存调整";
    String STOCK_ADJUST_SUCCESS = "调整库存批次【{{#createReqVO.stockLotId}}】，类型【{{#createReqVO.adjustmentType}}】，数量【{{#createReqVO.quantity}}】，备注【{{#createReqVO.remark}}】";

    String PUTAWAY_TYPE = "RD 入库上架";
    String PUTAWAY_SCAN_SUB_TYPE = "司机扫码上架";
    String PUTAWAY_SCAN_SUCCESS = "上架任务【{{#reqVO.id}}】扫描包码【{{#reqVO.packageCode}}】";
    String PUTAWAY_EXCEPTION_SUB_TYPE = "司机上报上架异常";
    String PUTAWAY_EXCEPTION_SUCCESS = "上架任务【{{#reqVO.id}}】上报异常【{{#reqVO.exceptionType}}】";
    String PUTAWAY_CONFIRM_SUB_TYPE = "仓管确认上架";
    String PUTAWAY_CONFIRM_SUCCESS = "确认上架任务【{{#reqVO.id}}】最终库位【{{#reqVO.finalLocationId}}】";

    String SALE_ORDER_TYPE = "RD 销售订单";
    String SALE_ORDER_CREATE_SUB_TYPE = "新增销售订单";
    String SALE_ORDER_CREATE_SUCCESS = "新增销售订单【{{#createReqVO.orderNo}}】，客户【{{#createReqVO.customerName}}】，型号【{{#createReqVO.productModel}}】，订单量【{{#createReqVO.requiredQty}}】";
    String SALE_ORDER_UPDATE_SUB_TYPE = "修改销售订单";
    String SALE_ORDER_UPDATE_SUCCESS = "修改销售订单【{{#updateReqVO.id}}】，单号【{{#updateReqVO.orderNo}}】，订单量【{{#updateReqVO.requiredQty}}】";
    String SALE_ORDER_CANCEL_SUB_TYPE = "取消销售订单";
    String SALE_ORDER_CANCEL_SUCCESS = "取消销售订单【{{#id}}】";
    String SALE_ORDER_RECALCULATE_SUB_TYPE = "重算销售订单";
    String SALE_ORDER_RECALCULATE_SUCCESS = "重新计算销售订单【{{#id}}】的实际产量、差额和完成状态";

    String FULFILLMENT_TYPE = "RD 订单匹配";
    String FULFILLMENT_ALLOCATE_SUB_TYPE = "人工匹配库存";
    String FULFILLMENT_ALLOCATE_SUCCESS = "订单【{{#allocateReqVO.orderId}}】匹配库存批次【{{#allocateReqVO.stockLotId}}】，数量【{{#allocateReqVO.quantity}}】";
    String FULFILLMENT_AUTO_ALLOCATE_SUB_TYPE = "自动匹配库存";
    String FULFILLMENT_AUTO_ALLOCATE_SUCCESS = "自动匹配销售订单【{{#orderId}}】的可用库存";
    String FULFILLMENT_CANCEL_SUB_TYPE = "取消库存匹配";
    String FULFILLMENT_CANCEL_SUCCESS = "取消库存匹配记录【{{#id}}】";

    String TRANSFER_TYPE = "RD 调拨单";
    String TRANSFER_CREATE_SUB_TYPE = "新增调拨单";
    String TRANSFER_CREATE_SUCCESS = "新增调拨单【{{#createReqVO.transferNo}}】，来源库存【{{#createReqVO.fromStockLotId}}】，调拨数量【{{#createReqVO.quantity}}】";
    String TRANSFER_UPDATE_SUB_TYPE = "修改调拨单";
    String TRANSFER_UPDATE_SUCCESS = "修改调拨单【{{#updateReqVO.id}}】，单号【{{#updateReqVO.transferNo}}】，调拨数量【{{#updateReqVO.quantity}}】";
    String TRANSFER_CONFIRM_SUB_TYPE = "确认调拨单";
    String TRANSFER_CONFIRM_SUCCESS = "确认调拨单【{{#id}}】，执行人【{{#operatorName}}】";
    String TRANSFER_CANCEL_SUB_TYPE = "取消调拨单";
    String TRANSFER_CANCEL_SUCCESS = "取消调拨单【{{#id}}】，执行人【{{#operatorName}}】";

    String OUTBOUND_TYPE = "RD 出库单";
    String OUTBOUND_CREATE_SUB_TYPE = "新增出库单";
    String OUTBOUND_CREATE_SUCCESS = "新增出库单【{{#createReqVO.outboundNo}}】，匹配记录【{{#createReqVO.fulfillmentId}}】，出库数量【{{#createReqVO.quantity}}】";
    String OUTBOUND_UPDATE_SUB_TYPE = "修改出库单";
    String OUTBOUND_UPDATE_SUCCESS = "修改出库单【{{#updateReqVO.id}}】，单号【{{#updateReqVO.outboundNo}}】，出库数量【{{#updateReqVO.quantity}}】";
    String OUTBOUND_CONFIRM_SUB_TYPE = "确认出库单";
    String OUTBOUND_CONFIRM_SUCCESS = "确认出库单【{{#id}}】，执行人【{{#operatorName}}】";
    String OUTBOUND_CANCEL_SUB_TYPE = "取消出库单";
    String OUTBOUND_CANCEL_SUCCESS = "取消出库单【{{#id}}】，执行人【{{#operatorName}}】";

    String EXCEL_IMPORT_TYPE = "RD Excel 导入";
    String EXCEL_IMPORT_PRECHECK_SUB_TYPE = "Excel 导入预检";
    String EXCEL_IMPORT_PRECHECK_SUCCESS = "完成 Excel 文件【{{#file.originalFilename}}】预检，业务日期【{{#bizDate}}】";
    String EXCEL_IMPORT_CONFIRM_SUB_TYPE = "确认 Excel 导入";
    String EXCEL_IMPORT_CONFIRM_SUCCESS = "确认 Excel 导入任务【{{#taskId}}】，执行人【{{#operatorName}}】";

    String DAILY_SUMMARY_TYPE = "RD 库存日报";
    String DAILY_SUMMARY_REBUILD_SUB_TYPE = "重建库存日报";
    String DAILY_SUMMARY_REBUILD_SUCCESS = "重建库存日报，日期范围【{{#startDate}}】至【{{#endDate}}】";

}
