package com.ruiding.erp.dal.dataobject.excelimport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@TableName("rd_excel_import_row")
@KeySequence("rd_excel_import_row_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdExcelImportRowDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private Integer rowNo;
    private String locationCode;
    private String customerName;
    private String productModel;
    private String furnaceNo;
    private String furnaceLayer;
    private BigDecimal sourceOpeningQty;
    private BigDecimal warehouseQty;
    private BigDecimal packageWeight;
    private BigDecimal stockWeight;
    private String status;
    private String errorMessage;
    private Long stockLotId;

}
