package com.ruiding.erp.controller.admin.stock.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import javax.validation.constraints.Min;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - RD 库存台账分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdStockLotPageReqVO extends PageParam {

    @Schema(description = "精确库位ID，与原货位模糊筛选独立", example = "1")
    @Min(1)
    private Long locationId;

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

    @Schema(description = "库存状态", example = "AVAILABLE")
    private String status;

    @Schema(description = "最后业务日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] lastBizDate;

}
