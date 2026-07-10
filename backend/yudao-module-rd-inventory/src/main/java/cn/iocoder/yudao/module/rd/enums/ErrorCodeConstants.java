package cn.iocoder.yudao.module.rd.enums;

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

    // ========== RD stock lot 1-080-200-000 ==========
    ErrorCode STOCK_LOT_NOT_EXISTS = new ErrorCode(1_080_200_000, "库存批次不存在");
    ErrorCode STOCK_LOT_ADJUST_TYPE_INVALID = new ErrorCode(1_080_200_001, "库存调整类型不正确");
    ErrorCode STOCK_LOT_ADJUST_NEGATIVE_BALANCE = new ErrorCode(1_080_200_002, "库存调减后不能小于 0");
    ErrorCode STOCK_LOT_ADJUST_CLOSED = new ErrorCode(1_080_200_003, "已关闭库存批次不能调整");

    // ========== RD stock transaction 1-080-210-000 ==========
    ErrorCode STOCK_TRANSACTION_NOT_EXISTS = new ErrorCode(1_080_210_000, "库存流水不存在");

}
