package com.ruiding.erp.controller.admin.production.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - RD 生产炉层产量新增/修改 Request VO")
@Data
public class RdProductionOutputSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "Version required for update", example = "1")
    private Integer version;
    @Positive private Long productionTaskId;
    @Size(max = 32) private String shiftCode;
    private LocalDate furnaceOutDate;
    @Size(max = 128) private String clientRequestId;

    @Schema(description = "产出日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "产出日期不能为空")
    private LocalDate productionDate;

    @Schema(description = "货位编号", example = "1001")
    private Long locationId;

    @Schema(description = "货位", example = "3-22")
    private String locationCode;

    @Schema(description = "客户", requiredMode = Schema.RequiredMode.REQUIRED, example = "贝特瑞产品一部")
    @NotEmpty(message = "客户不能为空")
    private String customerName;

    @Schema(description = "型号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13031")
    @NotEmpty(message = "型号不能为空")
    private String productModel;

    @Schema(description = "生产炉号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1-13#27")
    @NotEmpty(message = "生产炉号不能为空")
    private String furnaceNo;

    @Schema(description = "所在炉层", requiredMode = Schema.RequiredMode.REQUIRED, example = "下层（X）")
    @NotEmpty(message = "所在炉层不能为空")
    private String furnaceLayer;

    @Schema(description = "实际产额", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "实际产额不能为空")
    @DecimalMin(value = "0.000001", message = "实际产额必须大于 0")
    private BigDecimal outputQty;

    @Schema(description = "单包重量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "单包重量不能为空")
    @DecimalMin(value = "0.000001", message = "单包重量必须大于 0")
    private BigDecimal packageWeight;

    @Schema(description = "已入库数量", example = "0")
    @DecimalMin(value = "0.000000", message = "已入库数量不能小于 0")
    private BigDecimal inboundQty;

    @Schema(description = "状态", example = "DRAFT")
    private String status;

    @Schema(description = "来源表格")
    private String sourceSheet;

    @Schema(description = "备注")
    private String remark;

}
