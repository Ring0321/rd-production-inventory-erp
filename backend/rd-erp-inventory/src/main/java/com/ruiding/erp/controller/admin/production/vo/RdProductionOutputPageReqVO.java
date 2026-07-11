package com.ruiding.erp.controller.admin.production.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY;

@Schema(description = "管理后台 - RD 生产炉层产量分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdProductionOutputPageReqVO extends PageParam {

    @Schema(description = "产出日期范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY)
    private LocalDate[] productionDate;

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

    @Schema(description = "入库状态", example = "DRAFT")
    private String status;

}
