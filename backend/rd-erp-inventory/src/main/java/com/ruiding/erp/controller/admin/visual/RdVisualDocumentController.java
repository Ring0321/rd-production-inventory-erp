package com.ruiding.erp.controller.admin.visual;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruiding.erp.service.visual.RdVisualDocumentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController @RequestMapping("/rd/visual-document")
public class RdVisualDocumentController {
    @Resource private RdVisualDocumentService service;
    public record Save(@NotBlank @Pattern(regexp="LAYOUT|LABEL") String kind,
                       @NotBlank @Size(max=64) String scopeId, @NotNull @Min(0) Integer expectedVersion, @NotNull JsonNode content) { }
    public record Publish(@NotBlank @Pattern(regexp="LAYOUT|LABEL") String kind,
                          @NotBlank @Size(max=64) String scopeId, @NotNull @Min(0) Integer expectedVersion) { }
    @GetMapping("/get") @PreAuthorize("isAuthenticated()")
    public CommonResult<RdVisualDocumentService.View> get(@RequestParam String kind, @RequestParam String scopeId) {
        return success(service.get(kind, scopeId));
    }
    @PostMapping("/save") @PreAuthorize("isAuthenticated()")
    public CommonResult<RdVisualDocumentService.View> save(@Valid @RequestBody Save request) {
        return success(service.save(request.kind(), request.scopeId(), request.expectedVersion(), request.content()));
    }
    @PostMapping("/publish") @PreAuthorize("isAuthenticated()")
    public CommonResult<RdVisualDocumentService.View> publish(@Valid @RequestBody Publish request) {
        return success(service.publish(request.kind(), request.scopeId(), request.expectedVersion()));
    }
}
