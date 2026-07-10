package cn.iocoder.yudao.module.rd.dal.dataobject.transfer;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RD transfer order DO.
 */
@TableName("rd_transfer_order")
@KeySequence("rd_transfer_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdTransferOrderDO extends BaseDO {

    @TableId
    private Long id;
    private String transferNo;
    private LocalDate transferDate;
    private Long fromOrderId;
    private Long toOrderId;
    private Long fromStockLotId;
    private Long toStockLotId;
    private BigDecimal quantity;
    private BigDecimal packageWeight;
    private BigDecimal weight;
    private String status;
    private String remark;

}
