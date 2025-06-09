package com.example.speechmate_backend.common;

import com.example.speechmate_backend.common.error.ErrorCodeIfs;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T>{

    private String status;
    private Integer resultCode;
    private T data;

    public static <T> ApiResponse<T> ok(T data){
        return new ApiResponse<>("success", HttpStatus.OK.value(), data);
    }

    public static ApiResponse<String> fail(ErrorCodeIfs errorCode){
        return new ApiResponse<>(errorCode.getStatus(), errorCode.getResultCode(), errorCode.getMessage());
    }

    public static <T> ApiResponse<T> fail(String status, Integer code, T data){
        return new ApiResponse<>(status, code, data);
    }

}
