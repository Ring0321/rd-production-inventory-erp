package com.ruiding.erp.controller.admin.documentocr;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruiding.erp.service.documentocr.RdDocumentOcrException;
import com.ruiding.erp.service.documentocr.RdDocumentOcrService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/rd/document-ocr")
public class RdDocumentOcrController {
    private final RdDocumentOcrService service;

    public RdDocumentOcrController(RdDocumentOcrService service) { this.service = service; }

    @PostMapping(value = "/recognize", consumes = "multipart/form-data")
    @PreAuthorize("@rdDocumentOcrAccess.allowed()")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public ResponseEntity<CommonResult<JsonNode>> recognize(@RequestPart("file") MultipartFile file, MultipartHttpServletRequest request) {
        if (request.getQueryString() != null || !request.getParameterMap().isEmpty()
                || request.getMultiFileMap().size() != 1 || request.getFiles("file").size() != 1) {
            throw new RdDocumentOcrException("OCR_INVALID_REQUEST", 400);
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(success(service.recognize(file)));
    }

    @GetMapping("/status")
    @PreAuthorize("@rdDocumentOcrAccess.allowed()")
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public ResponseEntity<CommonResult<JsonNode>> status() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(success(service.status()));
    }

    @ExceptionHandler(RdDocumentOcrException.class)
    public ResponseEntity<CommonResult<Object>> failure(RdDocumentOcrException error) {
        return ResponseEntity.status(error.getStatus()).header("Cache-Control", "no-store")
                .body(CommonResult.error(error.getStatus(), error.getMessage()));
    }
}
