package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthCapabilitiesRespVO {

    private Boolean password;
    private Boolean captcha;
    private Boolean sms;
    private Boolean passwordReset;
    private Boolean registration;
    private List<Integer> socialProviders;
    private String qrProvider;

}
