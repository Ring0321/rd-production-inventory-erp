package cn.iocoder.yudao.module.rd.controller.admin.stock.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "管理后台 - RD 期初库存创建 Request VO")
@Data
public class RdStockOpeningCreateReqVO {

    @Schema(description = "业务日期", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "业务日期不能为空")
    private LocalDate bizDate;

    @Schema(description = "货位编号", example = "1001")
    private Long locationId;

    @Schema(description = "货位", requiredMode = Schema.RequiredMode.REQUIRED, example = "3-22")
    @NotEmpty(message = "货位不能为空")
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

    @Schema(description = "期初数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "期初数量不能为空")
    @DecimalMin(value = "0.000001", message = "期初数量必须大于 0")
    private BigDecimal quantity;

    @Schema(description = "单包重量", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "单包重量不能为空")
    @DecimalMin(value = "0.000001", message = "单包重量必须大于 0")
    private BigDecimal packageWeight;

    @Schema(description = "备注")
    private String remark;

}
