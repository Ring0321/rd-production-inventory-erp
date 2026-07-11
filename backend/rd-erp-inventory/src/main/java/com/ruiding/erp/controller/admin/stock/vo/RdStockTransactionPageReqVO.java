package com.ruiding.erp.controller.admin.stock.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - RD 库存流水分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdStockTransactionPageReqVO extends PageParam {

    @Schema(description = "库存批次编号", example = "1")
    private Long stockLotId;

    @Schema(description = "业务日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] bizDate;

    @Schema(description = "流水类型", example = "PRODUCTION_IN")
    private String transactionType;

    @Schema(description = "货位", example = "3-22")
    private String locationCode;

    @Schema(description = "客户", example = "贝特瑞产品一部")
    private String customerName;

    @Schema(description = "型号", example = "13031")
    private String productModel;

    @Schema(description = "生产炉号", example = "1-13#27")
    private String furnaceNo;

    @Schema(description = "所在炉层", example = "下层（X）")
    private String furnaceLayer;

    @Schema(description = "来源类型", example = "PRODUCTION_OUTPUT")
    private String sourceType;

    @Schema(description = "来源单号", example = "1024")
    private String sourceNo;

    @Schema(description = "关联订单编号", example = "2048")
    private Long relatedOrderId;

}
