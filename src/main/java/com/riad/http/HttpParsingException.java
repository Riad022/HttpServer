package com.riad.http;

public class HttpParsingException extends RuntimeException {

    final private HttpStatusCode errorCode;

    public HttpParsingException(HttpStatusCode httpStatusCode) {
        super(httpStatusCode.MESSAGE);
        this.errorCode = httpStatusCode;
    }
    public HttpStatusCode getErrorCode() {
        return errorCode;
    }
}
