package com.ruiding.erp.service.workassistant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;

import static com.ruiding.erp.service.workassistant.RdWorkQueryContract.*;

@Service
public class RdWorkQueryAccessPolicy {
    @Resource private PermissionService permissions;

    public AccessScope authorize(Request request) {
        LoginUser user = requireIdentity();
        if (request == null) {
            throw new ServiceException(400, "Work query is required");
        }
        Query query = request.queryType();
        if (query.reserved()) {
            throw new ServiceException(503, "Work alerts are not available");
        }
        // Enable warehouse filters only together with a reader that enforces them before aggregation.
        if (request.warehouseCode() != null) {
            throw new ServiceException(400, "Warehouse filtering is not available for this query");
        }
        boolean driver = permissions.hasAnyRoles(user.getId(), "rd_forklift_driver");
        String executionPermission = query == Query.Q06 ? "rd:putaway-task:execute"
                : query == Query.Q07 ? "rd:loading-task:execute" : null;
        if (driver) {
            if (executionPermission == null || !permissions.hasAnyPermissions(user.getId(), executionPermission)) {
                throw denied();
            }
            return new AccessScope(user.getTenantId(), user.getId(), DataScope.OWN_TASKS);
        }
        if (query.permissions().stream().allMatch(permission -> permissions.hasAnyPermissions(user.getId(), permission))) {
            return new AccessScope(user.getTenantId(), user.getId(), DataScope.TENANT_AUTHORIZED);
        }
        if (executionPermission != null && permissions.hasAnyPermissions(user.getId(), executionPermission)) {
            return new AccessScope(user.getTenantId(), user.getId(), DataScope.OWN_TASKS);
        }
        throw denied();
    }

    private static LoginUser requireIdentity() {
        var authentication = SecurityFrameworkUtils.getAuthentication();
        LoginUser user = SecurityFrameworkUtils.getLoginUser();
        Long tenantId = TenantContextHolder.getTenantId();
        if (authentication == null || !authentication.isAuthenticated() || user == null
                || user.getId() == null || user.getId() <= 0 || !Objects.equals(user.getUserType(), UserTypeEnum.ADMIN.getValue())
                || tenantId == null || tenantId <= 0 || TenantContextHolder.isIgnore()
                || !Objects.equals(tenantId, user.getTenantId())
                || user.getVisitTenantId() != null && !Objects.equals(tenantId, user.getVisitTenantId())) {
            throw denied();
        }
        return user;
    }

    /** Server-owned scope. Readers must apply it in SQL before counts, paging or joins. */
    public static final class AccessScope {
        private final Long tenantId;
        private final Long userId;
        private final DataScope dataScope;

        private AccessScope(Long tenantId, Long userId, DataScope dataScope) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.dataScope = dataScope;
        }

        public Long tenantId() { return tenantId; }
        public Long userId() { return userId; }
        public DataScope dataScope() { return dataScope; }

        public void requireSource(Long sourceTenantId, Long assignedDriverId) {
            LoginUser current = requireIdentity();
            if (!Objects.equals(current.getTenantId(), tenantId) || !Objects.equals(current.getId(), userId)
                    || !Objects.equals(sourceTenantId, tenantId)
                    || dataScope == DataScope.OWN_TASKS && !Objects.equals(assignedDriverId, userId)) {
                throw denied();
            }
        }
    }

    private static ServiceException denied() { return new ServiceException(403, "Work query access denied"); }
}
