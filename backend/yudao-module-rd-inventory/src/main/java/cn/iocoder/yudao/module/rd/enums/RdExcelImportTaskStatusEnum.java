package cn.iocoder.yudao.module.rd.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdExcelImportTaskStatusEnum {

    PRECHECKED("PRECHECKED"),
    CONFIRMING("CONFIRMING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String status;

}
