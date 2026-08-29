package com.agentsaul.dto;

public class ErrorResponse {

    private boolean success = false;
    private String errorCode;
    private String message;

    public static ErrorResponse of(String errorCode, String message) {
        ErrorResponse r = new ErrorResponse();
        r.errorCode = errorCode;
        r.message = message;
        return r;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
