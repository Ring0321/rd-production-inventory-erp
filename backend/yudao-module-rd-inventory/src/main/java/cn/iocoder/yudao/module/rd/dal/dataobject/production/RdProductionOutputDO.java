package cn.iocoder.yudao.module.rd.dal.dataobject.production;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RD furnace-layer production output DO.
 */
@TableName("rd_production_output")
@KeySequence("rd_production_output_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RdProductionOutputDO extends TenantBaseDO {

    /**
     * Production output ID.
     */
    @TableId
    private Long id;
    /**
     * Production date.
     */
    private LocalDate productionDate;
    /**
     * Default location ID.
     */
    private Long locationId;
    /**
     * Location code.
     */
    private String locationCode;
    /**
     * Customer.
     */
    private String customerName;
    /**
     * Product model.
     */
    private String productModel;
    /**
     * Production furnace number.
     */
    private String furnaceNo;
    /**
     * Furnace layer.
     */
    private String furnaceLayer;
    /**
     * Actual output quantity.
     */
    private BigDecimal outputQty;
    /**
     * Single package weight.
     */
    private BigDecimal packageWeight;
    /**
     * Output weight.
     */
    private BigDecimal outputWeight;
    /**
     * Inbound quantity generated from this output.
     */
    private BigDecimal inboundQty;
    /**
     * DRAFT/CONFIRMED/INBOUNDED/CANCELLED.
     */
    private String status;
    /**
     * Source sheet or Excel file.
     */
    private String sourceSheet;
    /**
     * Remark.
     */
    private String remark;

}
