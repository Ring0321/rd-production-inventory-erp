package cn.iocoder.yudao.module.rd.controller.admin.excelimport.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdExcelImportErrorPageReqVO extends PageParam {

    @NotNull(message = "导入任务编号不能为空")
    private Long taskId;

}
