package io.github.tissyboxc.clark_aams_backend.common;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(statusFor(exception.errorCode()))
                .body(ApiResponse.fail(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.BAD_REQUEST, ErrorCode.BAD_REQUEST.defaultMessage()));
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbort(ClientAbortException exception) {
        log.debug("Client closed connection before response was fully written: {}", exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled backend error", exception);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR));
    }

    private HttpStatus statusFor(ErrorCode errorCode) {
        return switch (errorCode) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case LOGIN_INVALID -> HttpStatus.UNAUTHORIZED;
            case SCHOOL_NOT_FOUND, IMPORTER_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USER_EMAIL_CONFLICT -> HttpStatus.CONFLICT;
            case ACADEMIC_REQUEST_FAILED, COURSE_PARSE_FAILED, INTERNAL_ERROR, AI_REQUEST_FAILED ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
            case OK -> HttpStatus.OK;
        };
    }
}
