package com.ruiding.erp.controller.admin.inbound.vo;
import com.ruiding.erp.dal.dataobject.inbound.RdInboundOrderLineDO;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;
@Data
public class RdInboundOrderCreateReqVO {
    @NotNull private LocalDate bizDate;
    @NotNull private Long productionOutputId;
    @NotEmpty private List<RdInboundOrderLineDO> lines;
    private String remark;
}
