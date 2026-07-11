package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.MenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.dal.mysql.tenant.TenantMapper;
import cn.iocoder.yudao.module.system.service.permission.MenuService;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantInfoHandler;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantMenuHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_DISABLE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_EXPIRE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.TENANT_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {

    public static final long FIXED_TENANT_ID = 1L;

    private final TenantMapper tenantMapper;
    private final MenuService menuService;

    @Override
    public List<Long> getTenantIdList() {
        return Collections.singletonList(FIXED_TENANT_ID);
    }

    @Override
    public void validTenant(Long id) {
        if (id == null || id != FIXED_TENANT_ID) {
            throw exception(TENANT_NOT_EXISTS);
        }
        TenantDO tenant = getTenant(id);
        if (tenant == null) {
            throw exception(TENANT_NOT_EXISTS);
        }
        if (CommonStatusEnum.DISABLE.getStatus().equals(tenant.getStatus())) {
            throw exception(TENANT_DISABLE, tenant.getName());
        }
        if (DateUtils.isExpired(tenant.getExpireTime())) {
            throw exception(TENANT_EXPIRE, tenant.getName());
        }
    }

    @Override
    public TenantDO getTenant(Long id) {
        return tenantMapper.selectById(id);
    }

    @Override
    public void handleTenantInfo(TenantInfoHandler handler) {
        Long tenantId = TenantContextHolder.getTenantId();
        handler.handle(getTenant(tenantId == null ? FIXED_TENANT_ID : tenantId));
    }

    @Override
    public void handleTenantMenu(TenantMenuHandler handler) {
        Set<Long> menuIds = CollectionUtils.convertSet(menuService.getMenuList(), MenuDO::getId);
        handler.handle(menuIds);
    }

}
