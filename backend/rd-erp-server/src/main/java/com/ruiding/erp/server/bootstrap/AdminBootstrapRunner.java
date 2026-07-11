package com.ruiding.erp.server.bootstrap;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String BOOTSTRAP_PASSWORD_SENTINEL = "!BOOTSTRAP_REQUIRED!";

    private final AdminUserService adminUserService;

    @Value("${rd.erp.bootstrap.admin-password:}")
    private String initialPassword;

    @Override
    public void run(ApplicationArguments args) {
        AdminUserDO admin = adminUserService.getUserByUsername("admin");
        if (admin == null || !BOOTSTRAP_PASSWORD_SENTINEL.equals(admin.getPassword())) {
            return;
        }
        if (StrUtil.length(initialPassword) < 12) {
            throw new IllegalStateException(
                    "RD_ADMIN_INITIAL_PASSWORD must contain at least 12 characters for first startup");
        }
        adminUserService.updateUserPassword(admin.getId(), initialPassword);
        log.info("Bootstrap administrator password initialized from deployment environment");
    }

}
