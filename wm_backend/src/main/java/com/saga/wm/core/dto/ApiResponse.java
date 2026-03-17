package com.saga.wm.core.dto;

/**
 * 공통 API 응답 래퍼
 * null 필드 제외는 application.yml의 spring.jackson.default-property-inclusion=non_null 로 처리
 *
 * 성공: {"success":true, "data":{...}}
 * 실패: {"success":false, "code":"TOKEN_EXPIRED", "message":"..."}
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String code,
        String message
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, code, message);
    }
}
