package com.ruiding.erp.service.documentocr;

public class RdDocumentOcrException extends RuntimeException {
    private final int status;

    public RdDocumentOcrException(String code, int status) {
        super(code);
        this.status = status;
    }

    public int getStatus() { return status; }
}
