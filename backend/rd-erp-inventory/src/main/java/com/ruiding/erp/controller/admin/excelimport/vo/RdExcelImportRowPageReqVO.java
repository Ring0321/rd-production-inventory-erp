package com.ruiding.erp.controller.admin.excelimport.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Schema(description = "管理后台 - RD Excel 导入暂存行分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdExcelImportRowPageReqVO extends PageParam {

    @NotNull(message = "导入任务编号不能为空")
    private Long taskId;

    @Schema(description = "行状态", example = "VALID")
    private String status;

}
