package com.archipelago.catalog;

import org.springframework.http.HttpStatus;

public class CatalogException extends RuntimeException {
    private final CatalogErrorKind errorKind;
    private final HttpStatus httpStatus;

    public CatalogException(CatalogErrorKind errorKind, HttpStatus httpStatus, String message) {
        super(message);
        this.errorKind = errorKind;
        this.httpStatus = httpStatus;
    }

    public CatalogException(CatalogErrorKind errorKind, HttpStatus httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.errorKind = errorKind;
        this.httpStatus = httpStatus;
    }

    public CatalogErrorKind getErrorKind() {
        return errorKind;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
