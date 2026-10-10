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
    ErrorCode PRODUCTION_OUTPUT_BOUND_LOCKED = new ErrorCode(1_080_100_011, "生产记录已绑定订单拆分，不能修改或作废");
    ErrorCode PRODUCTION_OUTPUT_CONFIRM_STATUS_INVALID = new ErrorCode(1_080_100_012, "只有草稿生产记录可以确认");
    ErrorCode PRODUCTION_OUTPUT_VERSION_CONFLICT = new ErrorCode(1_080_100_013, "生产记录已被其他人员更新，请刷新后重试");

    ErrorCode ORDER_SPLIT_NOT_EXISTS = new ErrorCode(1_080_320_000, "订单拆分结果不存在");
    ErrorCode ORDER_SPLIT_RULE_NOT_EXISTS = new ErrorCode(1_080_320_001, "订单拆分规则不存在");
    ErrorCode ORDER_SPLIT_STATUS_INVALID = new ErrorCode(1_080_320_002, "订单拆分当前状态不允许该操作");
    ErrorCode ORDER_SPLIT_VERSION_CONFLICT = new ErrorCode(1_080_320_003, "订单拆分已被其他人员更新，请刷新后重试");
    ErrorCode ORDER_SPLIT_BIND_INVALID = new ErrorCode(1_080_320_004, "生产产出与订单拆分的客户、型号、炉层或状态不匹配");
    ErrorCode ORDER_SPLIT_OUTPUT_BOUND = new ErrorCode(1_080_320_005, "生产产出已绑定订单拆分");

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
    ErrorCode PACKAGE_DAILY_SEQUENCE_EXHAUSTED = new ErrorCode(1_080_220_004, "当日包码流水已达到 9999，请联系管理员调整编码规则");
    ErrorCode LABEL_REPRINT_REASON_REQUIRED = new ErrorCode(1_080_220_005, "补打标签必须填写补打原因");
    ErrorCode LABEL_PRINT_SELECTION_INVALID = new ErrorCode(1_080_220_006, "请选择 1-500 个不同包件打印，补打原因不超过 500 字");

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
    ErrorCode TRANSFER_ORDER_PACKAGE_QTY_INVALID = new ErrorCode(1_080_400_009, "一包一码调拨数量必须为整数包");
    ErrorCode TRANSFER_ORDER_PACKAGE_INSUFFICIENT = new ErrorCode(1_080_400_010, "调出批次可用包码数量不足");
    ErrorCode TRANSFER_ORDER_PACKAGE_REVERSE_INVALID = new ErrorCode(1_080_400_011, "调拨包码已被后续业务占用，无法撤销调拨");

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
    ErrorCode OUTBOUND_ORDER_STATUS_INVALID = new ErrorCode(1_080_500_009, "当前出库单状态不允许执行该操作");
    ErrorCode OUTBOUND_PACKAGE_NOT_EXISTS = new ErrorCode(1_080_500_010, "包码不存在");
    ErrorCode OUTBOUND_PACKAGE_STATUS_INVALID = new ErrorCode(1_080_500_011, "包码不是在库状态，不能扫描出库");
    ErrorCode OUTBOUND_PACKAGE_DUPLICATE = new ErrorCode(1_080_500_012, "该包码已扫描，请勿重复扫描");
    ErrorCode OUTBOUND_PACKAGE_MISMATCH = new ErrorCode(1_080_500_013, "包码与销售订单客户、型号或分配批次不一致");
    ErrorCode OUTBOUND_ORDER_EMPTY = new ErrorCode(1_080_500_014, "出库单尚未扫描包码");
    ErrorCode OUTBOUND_ORDER_SELF_REVIEW = new ErrorCode(1_080_500_015, "制单人不能审核本人提交的出库单");
    ErrorCode OUTBOUND_ORDER_VERSION_CONFLICT = new ErrorCode(1_080_500_016, "出库单已被其他人员更新，请刷新后重试");
    ErrorCode OUTBOUND_PACKAGE_REMOVE_INVALID = new ErrorCode(1_080_500_017, "当前状态不能移除已扫描包码");

    // ========== RD stocktake 1-080-510-000 ==========
    ErrorCode STOCKTAKE_ORDER_NOT_EXISTS = new ErrorCode(1_080_510_000, "盘点单不存在");
    ErrorCode STOCKTAKE_ORDER_STATUS_INVALID = new ErrorCode(1_080_510_001, "当前盘点单状态不允许该操作");
    ErrorCode STOCKTAKE_ORDER_VERSION_CONFLICT = new ErrorCode(1_080_510_002, "盘点单已被其他人员更新，请刷新后重试");
    ErrorCode STOCKTAKE_LOCATION_INVALID = new ErrorCode(1_080_510_003, "盘点货位不存在、已停用或编码不匹配");
    ErrorCode STOCKTAKE_LOCATION_FROZEN_BY_OTHER = new ErrorCode(1_080_510_004, "当前货位已被其他盘点单冻结");
    ErrorCode STOCKTAKE_PACKAGE_NOT_EXISTS = new ErrorCode(1_080_510_005, "盘点包码不存在");
    ErrorCode STOCKTAKE_PACKAGE_STATUS_INVALID = new ErrorCode(1_080_510_006, "当前包码状态不允许盘点");
    ErrorCode STOCKTAKE_PACKAGE_DUPLICATE = new ErrorCode(1_080_510_007, "该包码已扫描，请勿重复盘点");
    ErrorCode STOCKTAKE_PACKAGE_LOCATION_MISMATCH = new ErrorCode(1_080_510_008, "包码不属于当前盘点货位");
    ErrorCode STOCKTAKE_ORDER_SELF_REVIEW = new ErrorCode(1_080_510_009, "提交人不得审核本人的盘点单");
    ErrorCode STOCKTAKE_ADJUST_BELOW_LOCKED = new ErrorCode(1_080_510_010, "盘点亏库后库存不能小于已锁定数量");

    // ========== RD putaway 1-080-520-000 ==========
    ErrorCode PUTAWAY_TASK_NOT_EXISTS = new ErrorCode(1_080_520_000, "上架任务不存在");
    ErrorCode PUTAWAY_SCAN_KEY_CONFLICT = new ErrorCode(1_080_520_017, "扫码请求已用于其他内容或操作人，请重新核对");
    ErrorCode PUTAWAY_BATCH_SELECTION_INVALID = new ErrorCode(1_080_520_018, "请选择 1-500 个不同的待核验包件");
    ErrorCode PUTAWAY_SCAN_PACKAGE_MISMATCH = new ErrorCode(1_080_520_019, "代表包或所选包件不属于当前上架任务");
    ErrorCode PUTAWAY_TASK_STATUS_INVALID = new ErrorCode(1_080_520_001, "当前上架任务状态不允许该操作");
    ErrorCode PUTAWAY_TASK_VERSION_CONFLICT = new ErrorCode(1_080_520_002, "上架任务已被其他人员更新，请刷新后重试");
    ErrorCode PUTAWAY_PACKAGE_STATUS_INVALID = new ErrorCode(1_080_520_003, "仅质检合格且未派单的包允许创建上架任务");
    ErrorCode PUTAWAY_PACKAGE_DATA_INCOMPLETE = new ErrorCode(1_080_520_004, "包码关键货物信息不完整，不能派发上架任务");
    ErrorCode PUTAWAY_PACKAGE_DUPLICATE = new ErrorCode(1_080_520_005, "上架任务中存在重复包码");
    ErrorCode PUTAWAY_LOCATION_INVALID = new ErrorCode(1_080_520_006, "计划库位不存在、已停用、已冻结或当前不可用");
    ErrorCode PUTAWAY_LOCATION_CAPACITY_INSUFFICIENT = new ErrorCode(1_080_520_007, "计划库位剩余容量不足，请选择其他库位");
    ErrorCode PUTAWAY_DRIVER_INVALID = new ErrorCode(1_080_520_008, "所选人员不是已启用的叉车司机");
    ErrorCode PUTAWAY_REASSIGN_REASON_REQUIRED = new ErrorCode(1_080_520_009, "调整上架任务必须填写处理说明");
    ErrorCode PUTAWAY_TASK_NOT_ASSIGNED = new ErrorCode(1_080_520_010, "该上架任务未分配给当前司机");
    ErrorCode PUTAWAY_EXCEPTION_TYPE_INVALID = new ErrorCode(1_080_520_011, "请选择有效的上架异常原因");
    ErrorCode PUTAWAY_CONFIRM_PACKAGES_INCOMPLETE = new ErrorCode(1_080_520_012, "任务包码尚未全部扫码，不能确认上架");
    ErrorCode PUTAWAY_CONFIRM_HAS_OPEN_EXCEPTION = new ErrorCode(1_080_520_013, "任务仍有未处理异常，不能确认上架");
    ErrorCode PUTAWAY_CONFIRM_PACKAGE_INVALID = new ErrorCode(1_080_520_014, "上架包状态或任务明细不一致，请刷新核对");

    // ========== RD loading dispatch 1-080-530-000 ==========
    ErrorCode PICKUP_SLIP_NOT_EXISTS = new ErrorCode(1_080_530_000, "提货单不存在");
    ErrorCode PICKUP_SLIP_DUPLICATE = new ErrorCode(1_080_530_001, "提货单号已存在");
    ErrorCode PICKUP_SLIP_INVALID = new ErrorCode(1_080_530_002, "提货单明细、数量或重量不完整或不一致");
    ErrorCode PICKUP_SLIP_ORDER_MISMATCH = new ErrorCode(1_080_530_003, "提货单订单与客户或型号不匹配");
    ErrorCode LOADING_TASK_ALREADY_ASSIGNED = new ErrorCode(1_080_530_004, "该提货单已有有效装车任务");
    ErrorCode LOADING_DRIVER_INVALID = new ErrorCode(1_080_530_005, "所选人员不是已启用的叉车司机");
    ErrorCode LOADING_PACKAGE_INVALID = new ErrorCode(1_080_530_006, "所选包不在库或货物、订单、库位信息与提货单不符");
    ErrorCode LOADING_PACKAGE_CLAIMED = new ErrorCode(1_080_530_007, "所选包已被其他装车任务占用，请刷新库存");
    ErrorCode LOADING_TASK_NOT_EXISTS = new ErrorCode(1_080_530_008, "装车任务不存在或未分配给当前司机");
    ErrorCode LOADING_QUANTITY_MISMATCH = new ErrorCode(1_080_530_009, "选包数量或重量与提货单明细不一致");
    ErrorCode LOADING_FULFILLMENT_INSUFFICIENT = new ErrorCode(1_080_530_010, "订单匹配的可发数量不足或尚未分配库存批次");
    ErrorCode LOADING_TASK_STATUS_INVALID = new ErrorCode(1_080_530_011, "装车任务当前状态不允许执行该操作");
    ErrorCode LOADING_PACKAGE_NOT_IN_TASK = new ErrorCode(1_080_530_012, "扫描包码不属于当前有效装车任务");
    ErrorCode LOADING_PACKAGE_DUPLICATE = new ErrorCode(1_080_530_013, "所选包件已完成装车扫码，请勿重复确认");
    ErrorCode LOADING_TASK_VERSION_CONFLICT = new ErrorCode(1_080_530_014, "装车任务已被其他人员更新，请刷新后重试");
    ErrorCode LOADING_CONFIRM_PACKAGES_INCOMPLETE = new ErrorCode(1_080_530_015, "装车任务包件尚未全部复核，不能确认出库");
    ErrorCode LOADING_CONFIRM_HAS_OPEN_EXCEPTION = new ErrorCode(1_080_530_016, "装车任务仍有未处理异常，不能确认出库");
    ErrorCode LOADING_EXCEPTION_TYPE_INVALID = new ErrorCode(1_080_530_017, "请选择有效的装车异常原因");
    ErrorCode LOADING_EXCEPTION_HANDLE_REQUIRED = new ErrorCode(1_080_530_018, "处理装车异常必须填写处理说明");
    ErrorCode LOADING_EXCEPTION_ACTION_INVALID = new ErrorCode(1_080_530_019, "请选择有效的装车异常处理方式");
    ErrorCode LOADING_EXCEPTION_NOT_EXISTS = new ErrorCode(1_080_530_020, "装车任务没有待处理异常");
    ErrorCode LOADING_REPLACEMENT_REQUIRED = new ErrorCode(1_080_530_021, "换货处理必须选择原任务包和替换包码");
    ErrorCode LOADING_REPLACEMENT_LINE_INVALID = new ErrorCode(1_080_530_022, "只能替换未出库且未完成扫码的任务包件");

    // ========== RD Excel import 1-080-600-000 ==========
    ErrorCode EXCEL_IMPORT_TASK_NOT_EXISTS = new ErrorCode(1_080_600_000, "Excel 导入任务不存在");
    ErrorCode EXCEL_IMPORT_FILE_INVALID = new ErrorCode(1_080_600_001, "请上传不超过 20 MB 的 xlsx 文件");
    ErrorCode EXCEL_IMPORT_TASK_STATUS_INVALID = new ErrorCode(1_080_600_002, "当前导入任务状态不能确认");
    ErrorCode EXCEL_IMPORT_TASK_HAS_ERRORS = new ErrorCode(1_080_600_003, "预检存在错误，请修正 Excel 后重新上传");
    ErrorCode EXCEL_IMPORT_TASK_NO_VALID_ROWS = new ErrorCode(1_080_600_004, "没有可导入的正库存数据");
    ErrorCode EXCEL_IMPORT_PARSE_FAILED = new ErrorCode(1_080_600_005, "库存 Excel 解析失败：{}");

}
