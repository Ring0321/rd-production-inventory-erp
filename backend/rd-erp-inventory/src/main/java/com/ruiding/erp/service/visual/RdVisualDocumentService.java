package com.ruiding.erp.service.visual;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.dal.dataobject.masterdata.RdWarehouseLocationDO;
import com.ruiding.erp.dal.dataobject.visual.RdVisualDocumentDO;
import com.ruiding.erp.dal.dataobject.visual.RdVisualDocumentEventDO;
import com.ruiding.erp.dal.mysql.masterdata.RdWarehouseLocationMapper;
import com.ruiding.erp.dal.mysql.visual.RdVisualDocumentEventMapper;
import com.ruiding.erp.dal.mysql.visual.RdVisualDocumentMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class RdVisualDocumentService {
    private static final ErrorCode CONFLICT = new ErrorCode(1080900001, "配置版本已变化，请重新读取后核对，未覆盖其他人的修改");
    @Resource private RdVisualDocumentMapper documents;
    @Resource private RdVisualDocumentEventMapper events;
    @Resource private RdWarehouseLocationMapper locations;
    @Resource private PermissionApi permissions;
    @Resource private RdVisualContentValidator validator;
    @Resource private ObjectMapper json;

    public record View(String kind, String scopeId, int version, JsonNode draft,
                       JsonNode published, int publishedVersion, LocalDateTime updatedAt) { }

    public View get(String kind, String scopeId) {
        authorize(kind, false); scope(kind, scopeId);
        return view(kind, scopeId, documents.getExact(kind, scopeId));
    }

    @Transactional(rollbackFor = Exception.class)
    public View save(String kind, String scopeId, Integer expectedVersion, JsonNode content) {
        authorize(kind, true); scope(kind, scopeId);
        var normalized = validator.validate(kind, scopeId, content, warehouseLocations(kind, scopeId));
        var row = documents.lock(kind, scopeId);
        checkVersion(row, expectedVersion);
        boolean fresh = row == null;
        if (fresh) { row = new RdVisualDocumentDO(); row.setKind(kind); row.setScopeId(scopeId); row.setPublishedVersion(0); }
        row.setVersion(expectedVersion + 1); row.setDraftContent(normalized.toString());
        try { if (fresh) documents.insert(row); else documents.updateById(row); }
        catch (DuplicateKeyException e) { throw exception(CONFLICT); }
        event(row, "SAVE", normalized.toString());
        return view(kind, scopeId, documents.selectById(row.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public View publish(String kind, String scopeId, Integer expectedVersion) {
        authorize(kind, true); scope(kind, scopeId);
        var row = documents.lock(kind, scopeId); checkVersion(row, expectedVersion);
        if (row == null || row.getDraftContent() == null) throw new IllegalArgumentException("请先保存有效草稿");
        var content = validator.validate(kind, scopeId, parse(row.getDraftContent()), warehouseLocations(kind, scopeId));
        row.setVersion(expectedVersion + 1); row.setPublishedVersion(row.getVersion()); row.setPublishedContent(content.toString());
        documents.updateById(row); event(row, "PUBLISH", content.toString());
        return view(kind, scopeId, documents.selectById(row.getId()));
    }

    private void authorize(String kind, boolean write) {
        var user = SecurityFrameworkUtils.getLoginUserId();
        if (user == null || permissions.hasAnyRoles(user, "rd_forklift_driver")) throw exception(FORBIDDEN);
        if ("LAYOUT".equals(kind)) {
            if (!permissions.hasAnyPermissions(user, "rd:warehouse-location:query")
                    || !permissions.hasAnyPermissions(user, "rd:putaway-task:query")
                    || (write && !permissions.hasAnyPermissions(user, "rd:warehouse-location:update"))) throw exception(FORBIDDEN);
        } else if ("LABEL".equals(kind)) {
            if (!permissions.hasAnyPermissions(user, "rd:package-unit:query")
                    || (write && !permissions.hasAnyRoles(user, "super_admin"))) throw exception(FORBIDDEN);
        } else throw new IllegalArgumentException("配置类型不支持");
    }
    private void scope(String kind, String scope) {
        if (scope == null || scope.isBlank() || scope.length() > 64 || scope.matches("(?s).*[\\p{Cntrl}].*"))
            throw new IllegalArgumentException("配置范围无效");
        if ("LABEL".equals(kind) && !"GLOBAL".equals(scope)) throw new IllegalArgumentException("标签模板范围无效");
        if ("LAYOUT".equals(kind) && warehouseLocations(kind, scope).isEmpty()) throw new IllegalArgumentException("仓库编码不存在");
    }
    private Map<Long, RdWarehouseLocationDO> warehouseLocations(String kind, String scope) {
        if (!"LAYOUT".equals(kind)) return Map.of();
        return locations.selectList(new LambdaQueryWrapper<RdWarehouseLocationDO>().eq(RdWarehouseLocationDO::getWarehouseCode, scope))
                .stream().filter(l -> scope.equals(l.getWarehouseCode())).collect(Collectors.toMap(RdWarehouseLocationDO::getId, l -> l));
    }
    private static void checkVersion(RdVisualDocumentDO row, Integer expected) {
        if (expected == null || expected < 0 || expected == Integer.MAX_VALUE) throw new IllegalArgumentException("配置版本无效");
        if (expected != (row == null ? 0 : row.getVersion())) throw exception(CONFLICT);
    }
    private void event(RdVisualDocumentDO row, String action, String content) {
        var event = new RdVisualDocumentEventDO(); event.setDocumentId(row.getId()); event.setVersion(row.getVersion());
        event.setAction(action); event.setContent(content); events.insert(event);
    }
    private View view(String kind, String scope, RdVisualDocumentDO row) {
        return row == null ? new View(kind, scope, 0, null, null, 0, null)
                : new View(kind, scope, row.getVersion(), parse(row.getDraftContent()), parse(row.getPublishedContent()), row.getPublishedVersion(), row.getUpdateTime());
    }
    private JsonNode parse(String value) {
        if (value == null) return null;
        try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException("配置内容无法读取，请核对原记录"); }
    }
}
