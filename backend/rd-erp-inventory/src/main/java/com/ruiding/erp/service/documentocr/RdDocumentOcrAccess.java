package com.ruiding.erp.service.documentocr;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Component("rdDocumentOcrAccess")
public class RdDocumentOcrAccess {
    private final PermissionCommonApi permissions;

    public RdDocumentOcrAccess(PermissionCommonApi permissions) { this.permissions = permissions; }

    public boolean allowed() {
        Long actor = getLoginUserId();
        return actor != null && !permissions.hasAnyRoles(actor, "rd_forklift_driver")
                && permissions.hasAnyPermissions(actor, "rd:pickup-slip:create", "rd:quality-inspection:create");
    }
}
