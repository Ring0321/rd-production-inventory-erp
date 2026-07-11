package cn.iocoder.yudao.module.system.service.tenant;

import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantInfoHandler;
import cn.iocoder.yudao.module.system.service.tenant.handler.TenantMenuHandler;

import java.util.List;

public interface TenantService {

    List<Long> getTenantIdList();

    void validTenant(Long id);

    TenantDO getTenant(Long id);

    void handleTenantInfo(TenantInfoHandler handler);

    void handleTenantMenu(TenantMenuHandler handler);

}
