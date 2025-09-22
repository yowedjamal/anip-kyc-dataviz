package com.anip.kyc.dto.common;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String code;
    private String message;

    public ApiResponse() {}

    public ApiResponse(boolean success, T data, String code, String message) {
        this.success = success;
        this.data = data;
        this.code = code;
        this.message = message;
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, null, message);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, code, message);
    }

    public static <T> ApiResponse<T> error(String code, String message, T data) {
        return new ApiResponse<>(false, data, code, message);
    }

    // Getters/Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
