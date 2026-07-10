package cn.iocoder.yudao.module.rd.dal.dataobject.outbound;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * RD outbound order DO.
 */
@TableName("rd_outbound_order")
@KeySequence("rd_outbound_order_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdOutboundOrderDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String outboundNo;
    private LocalDate outboundDate;
    private Long orderId;
    private Long fulfillmentId;
    private Long stockLotId;
    private BigDecimal quantity;
    private BigDecimal packageWeight;
    private BigDecimal weight;
    private String status;
    private String remark;

}
