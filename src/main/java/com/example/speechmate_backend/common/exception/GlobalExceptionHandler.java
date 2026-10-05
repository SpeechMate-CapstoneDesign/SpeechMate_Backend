package com.example.speechmate_backend.common.exception;

import com.example.speechmate_backend.common.ApiResponse;
import com.example.speechmate_backend.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.client.RedisException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     *  Validation 실패 (MethodArgumentNotValidException)
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Validation Error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        for(FieldError fieldError : ex.getBindingResult().getFieldErrors()){
            String fileName = fieldError.getField().contains(".") ?
                    fieldError.getField().substring(fieldError.getField().lastIndexOf(".") + 1) :
                    fieldError.getField();
            errors.put(fileName, fieldError.getDefaultMessage());
        }
        return ResponseEntity
                .status(status)
                .body(ApiResponse.fail(
                        "Validation failed",
                        HttpStatus.BAD_REQUEST.value(),
                        errors
                ));
    }

    /**
     *  필수 요청 파라미터 누락 (MissingServletRequestParameterException)
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.error("Missing Parameter: {}", ex.getMessage());

        return ResponseEntity
                .status(status)
                .body(ApiResponse.fail(
                        "Missing parameter",
                        HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage()
                ));
    }


    @ExceptionHandler(SmateException.class)
    public ResponseEntity<ApiResponse<String>> handleSmateException(SmateException ex) {
        return ResponseEntity.status(ex.getError().getResultCode())
                .body(ApiResponse.fail(ex.getError()));
    }

    /** 위 어디에도 안 걸린 예외. 스택은 서버 로그에만 남기고 클라이언트엔 같은 봉투의 500만 준다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getResultCode())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    /**
     * Redis 장애 (페일오버 다운타임 등) — 500 대신 명확한 503으로 응답.
     * access token 검증은 Redis를 타지 않으므로 일반 API는 영향 없고,
     * 로그인/재발급/로그아웃과 분산 락 구간만 이 응답을 받는다.
     */
    @ExceptionHandler({RedisConnectionFailureException.class, RedisException.class})
    public ResponseEntity<ApiResponse<String>> handleRedisFailure(Exception ex) {
        log.error("Redis 연결 장애: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(
                        "일시적인 저장소 장애입니다. 잠시 후 다시 시도해주세요.",
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        null
                ));
    }

}
