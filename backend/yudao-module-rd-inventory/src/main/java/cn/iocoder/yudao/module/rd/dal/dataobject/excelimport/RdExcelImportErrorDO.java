package cn.iocoder.yudao.module.rd.dal.dataobject.excelimport;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@TableName("rd_excel_import_error")
@KeySequence("rd_excel_import_error_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class RdExcelImportErrorDO extends BaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private Integer rowNo;
    private String fieldName;
    private String rawValue;
    private String errorMessage;

}
