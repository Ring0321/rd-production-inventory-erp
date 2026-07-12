package com.ruiding.erp.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * RD inventory error code constants.
 *
 * <p>RD inventory uses 1-080-000-000 range.</p>
 */
public interface ErrorCodeConstants {

    // ========== RD production output 1-080-100-000 ==========
    ErrorCode PRODUCTION_OUTPUT_NOT_EXISTS = new ErrorCode(1_080_100_000, "生产炉层产量不存在");
    ErrorCode PRODUCTION_OUTPUT_STATUS_INVALID = new ErrorCode(1_080_100_001, "生产炉层产量状态不正确");
    ErrorCode PRODUCTION_OUTPUT_UPDATE_FAIL_INBOUNDED = new ErrorCode(1_080_100_002, "生产炉层产量已入库，无法修改");
    ErrorCode PRODUCTION_OUTPUT_CANCEL_FAIL_INBOUNDED = new ErrorCode(1_080_100_003, "生产炉层产量已入库，无法作废");
    ErrorCode PRODUCTION_OUTPUT_CANCEL_FAIL_CANCELLED = new ErrorCode(1_080_100_004, "生产炉层产量已作废");
    ErrorCode PRODUCTION_OUTPUT_INBOUND_FAIL_INBOUNDED = new ErrorCode(1_080_100_005, "生产炉层产量已入库，不能重复入库");
    ErrorCode PRODUCTION_OUTPUT_INBOUND_FAIL_CANCELLED = new ErrorCode(1_080_100_006, "生产炉层产量已作废，无法入库");
    ErrorCode PRODUCTION_OUTPUT_INBOUND_FAIL_LOCATION_EMPTY = new ErrorCode(1_080_100_007, "生产炉层产量货位为空，无法入库");
    ErrorCode PRODUCTION_OUTPUT_PACKAGE_REQUIRED = new ErrorCode(1_080_100_008, "请先生成包码并完成质检、入库单流程，禁止生产记录直接入库");
    ErrorCode PRODUCTION_OUTPUT_USE_INBOUND_ORDER = new ErrorCode(1_080_100_009, "生产记录已有包码，请通过质检合格后的入库单完成入库");
    ErrorCode PRODUCTION_OUTPUT_PACKAGE_LOCKED = new ErrorCode(1_080_100_010, "生产记录已生成包码，不能修改或作废");

    // ========== RD stock lot 1-080-200-000 ==========
    ErrorCode STOCK_LOT_NOT_EXISTS = new ErrorCode(1_080_200_000, "库存批次不存在");
    ErrorCode STOCK_LOT_ADJUST_TYPE_INVALID = new ErrorCode(1_080_200_001, "库存调整类型不正确");
    ErrorCode STOCK_LOT_ADJUST_NEGATIVE_BALANCE = new ErrorCode(1_080_200_002, "库存调减后不能小于 0");
    ErrorCode STOCK_LOT_ADJUST_CLOSED = new ErrorCode(1_080_200_003, "已关闭库存批次不能调整");
    ErrorCode STOCK_LOT_ADJUST_BELOW_LOCKED = new ErrorCode(1_080_200_004, "库存调减后不能小于已锁定数量");

    // ========== RD stock transaction 1-080-210-000 ==========
    ErrorCode STOCK_TRANSACTION_NOT_EXISTS = new ErrorCode(1_080_210_000, "库存流水不存在");

    // ========== RD package 1-080-220-000 ==========
    ErrorCode PACKAGE_NOT_EXISTS = new ErrorCode(1_080_220_000, "包码不存在");
    ErrorCode PACKAGE_STATUS_INVALID = new ErrorCode(1_080_220_001, "包码当前状态不允许该操作");
    ErrorCode PACKAGE_ALREADY_GENERATED = new ErrorCode(1_080_220_002, "该生产记录已生成包码，不能重复生成");
    ErrorCode PACKAGE_COUNT_MISMATCH = new ErrorCode(1_080_220_003, "拆包数量必须等于生产记录产量包数");
    ErrorCode PACKAGE_DAILY_SEQUENCE_EXHAUSTED = new ErrorCode(1_080_220_004, "当日包码流水已达到 999，请联系管理员调整编码规则");
    ErrorCode LABEL_REPRINT_REASON_REQUIRED = new ErrorCode(1_080_220_005, "补打标签必须填写补打原因");

    // ========== RD quality 1-080-230-000 ==========
    ErrorCode QUALITY_INSPECTION_NOT_EXISTS = new ErrorCode(1_080_230_000, "质检单不存在");
    ErrorCode QUALITY_INSPECTION_STATUS_INVALID = new ErrorCode(1_080_230_001, "质检单当前状态不允许该操作");
    ErrorCode QUALITY_INSPECTION_SELF_REVIEW = new ErrorCode(1_080_230_002, "制单人不得审核本人质检单");
    ErrorCode QUALITY_INSPECTION_ITEMS_NOT_QUALIFIED = new ErrorCode(1_080_230_003, "存在未填写或不合格的检测项目，不能审核通过");

    // ========== RD inbound 1-080-240-000 ==========
    ErrorCode INBOUND_ORDER_NOT_EXISTS = new ErrorCode(1_080_240_000, "入库单不存在");
    ErrorCode INBOUND_ORDER_STATUS_INVALID = new ErrorCode(1_080_240_001, "入库单当前状态不允许该操作");
    ErrorCode INBOUND_ORDER_SELF_REVIEW = new ErrorCode(1_080_240_002, "制单人不得复核本人入库单");
    ErrorCode INBOUND_PACKAGE_QC_REQUIRED = new ErrorCode(1_080_240_003, "仅质检合格包码允许加入入库单");
    ErrorCode INBOUND_REVERSE_STOCK_USED = new ErrorCode(1_080_240_004, "入库库存已被后续业务占用，无法撤销");
    ErrorCode INBOUND_PACKAGE_PRODUCTION_MISMATCH = new ErrorCode(1_080_240_005, "包码不属于当前生产记录");
    ErrorCode INBOUND_LOCATION_INVALID = new ErrorCode(1_080_240_006, "入库货位不存在、已停用、已冻结或编码不匹配");

    // ========== RD master data 1-080-250-000 ==========
    ErrorCode PRODUCT_MASTER_NOT_EXISTS = new ErrorCode(1_080_250_000, "产品资料不存在");
    ErrorCode WAREHOUSE_LOCATION_NOT_EXISTS = new ErrorCode(1_080_250_001, "仓库货位不存在");
    ErrorCode WAREHOUSE_LOCATION_FROZEN = new ErrorCode(1_080_250_002, "仓库货位已冻结，不能入库");

    // ========== RD sale order 1-080-300-000 ==========
    ErrorCode SALE_ORDER_NOT_EXISTS = new ErrorCode(1_080_300_000, "销售订单不存在");
    ErrorCode SALE_ORDER_NO_EXISTS = new ErrorCode(1_080_300_001, "销售订单号已存在");
    ErrorCode SALE_ORDER_STATUS_CANNOT_CHANGE = new ErrorCode(1_080_300_002, "当前销售订单状态不允许修改");
    ErrorCode SALE_ORDER_ALLOCATION_EXCEEDS_DIFFERENCE = new ErrorCode(1_080_300_003, "匹配数量不能超过订单剩余差额");
    ErrorCode SALE_ORDER_ALLOCATION_NEGATIVE = new ErrorCode(1_080_300_004, "订单已分配数量不能小于 0");
    ErrorCode SALE_ORDER_TRANSFER_NEGATIVE = new ErrorCode(1_080_300_005, "订单调入或调出数量不能小于 0");
    ErrorCode SALE_ORDER_TRANSFER_EXCEEDS_DIFFERENCE = new ErrorCode(1_080_300_006, "调入数量不能超过订单剩余差额");
    ErrorCode SALE_ORDER_OUTBOUND_EXISTS = new ErrorCode(1_080_300_007, "销售订单已有确认出库记录，不能取消");

    // ========== RD order fulfillment 1-080-310-000 ==========
    ErrorCode ORDER_FULFILLMENT_NOT_EXISTS = new ErrorCode(1_080_310_000, "订单库存匹配记录不存在");
    ErrorCode ORDER_FULFILLMENT_DUPLICATE = new ErrorCode(1_080_310_001, "该订单已匹配当前库存批次");
    ErrorCode ORDER_FULFILLMENT_STATUS_CANNOT_CANCEL = new ErrorCode(1_080_310_002, "当前匹配状态不能取消");
    ErrorCode ORDER_MATCH_STOCK_INSUFFICIENT = new ErrorCode(1_080_310_003, "库存批次可用数量不足");
    ErrorCode ORDER_MATCH_STOCK_DIMENSION_MISMATCH = new ErrorCode(1_080_310_004, "直接匹配必须使用相同客户和型号的库存");

    // ========== RD transfer order 1-080-400-000 ==========
    ErrorCode TRANSFER_ORDER_NOT_EXISTS = new ErrorCode(1_080_400_000, "调拨单不存在");
    ErrorCode TRANSFER_ORDER_NO_EXISTS = new ErrorCode(1_080_400_001, "调拨单号已存在");
    ErrorCode TRANSFER_ORDER_STATUS_NOT_DRAFT = new ErrorCode(1_080_400_002, "只有草稿调拨单可以修改或确认");
    ErrorCode TRANSFER_ORDER_STATUS_CANNOT_CANCEL = new ErrorCode(1_080_400_003, "当前调拨单状态不能撤销");
    ErrorCode TRANSFER_ORDER_STOCK_INSUFFICIENT = new ErrorCode(1_080_400_004, "调出库存可用数量不足");
    ErrorCode TRANSFER_ORDER_MODEL_MISMATCH = new ErrorCode(1_080_400_005, "调出库存与调入订单型号不一致");
    ErrorCode TRANSFER_ORDER_SAME_CUSTOMER = new ErrorCode(1_080_400_006, "相同客户库存请使用订单匹配，无需调拨");
    ErrorCode TRANSFER_ORDER_FROM_ORDER_MISMATCH = new ErrorCode(1_080_400_007, "调出订单与调出库存客户或型号不一致");
    ErrorCode TRANSFER_ORDER_REVERSE_INSUFFICIENT = new ErrorCode(1_080_400_008, "调入库存已被占用或数量不足，无法撤销调拨");

    // ========== RD outbound order 1-080-500-000 ==========
    ErrorCode OUTBOUND_ORDER_NOT_EXISTS = new ErrorCode(1_080_500_000, "出库单不存在");
    ErrorCode OUTBOUND_ORDER_NO_EXISTS = new ErrorCode(1_080_500_001, "出库单号已存在");
    ErrorCode OUTBOUND_ORDER_STATUS_NOT_DRAFT = new ErrorCode(1_080_500_002, "只有草稿出库单可以修改或确认");
    ErrorCode OUTBOUND_ORDER_STATUS_CANNOT_CANCEL = new ErrorCode(1_080_500_003, "当前出库单状态不能撤销");
    ErrorCode OUTBOUND_ORDER_FULFILLMENT_MISMATCH = new ErrorCode(1_080_500_004, "出库单与订单匹配记录不一致");
    ErrorCode OUTBOUND_ORDER_QUANTITY_EXCEEDS_ALLOCATION = new ErrorCode(1_080_500_005, "出库数量超过匹配记录剩余可出库数量");
    ErrorCode OUTBOUND_ORDER_STOCK_INSUFFICIENT = new ErrorCode(1_080_500_006, "已锁定库存不足，无法确认出库");
    ErrorCode OUTBOUND_ORDER_DRAFT_EXISTS = new ErrorCode(1_080_500_007, "该匹配记录已有出库草稿，请先处理原草稿");
    ErrorCode OUTBOUND_ORDER_REVERSE_INSUFFICIENT = new ErrorCode(1_080_500_008, "出库数量已被后续业务占用，无法撤销");

    // ========== RD Excel import 1-080-600-000 ==========
    ErrorCode EXCEL_IMPORT_TASK_NOT_EXISTS = new ErrorCode(1_080_600_000, "Excel 导入任务不存在");
    ErrorCode EXCEL_IMPORT_FILE_INVALID = new ErrorCode(1_080_600_001, "请上传不超过 20 MB 的 xlsx 文件");
    ErrorCode EXCEL_IMPORT_TASK_STATUS_INVALID = new ErrorCode(1_080_600_002, "当前导入任务状态不能确认");
    ErrorCode EXCEL_IMPORT_TASK_HAS_ERRORS = new ErrorCode(1_080_600_003, "预检存在错误，请修正 Excel 后重新上传");
    ErrorCode EXCEL_IMPORT_TASK_NO_VALID_ROWS = new ErrorCode(1_080_600_004, "没有可导入的正库存数据");
    ErrorCode EXCEL_IMPORT_PARSE_FAILED = new ErrorCode(1_080_600_005, "库存 Excel 解析失败：{}");

}
