package com.mallota.exception;

import com.mallota.dto.response.ApiErrorResponse;
import com.mallota.dto.response.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return response(exception.getErrorCode(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        Optional.ofNullable(error.getDefaultMessage())
                                .orElse("올바른 값을 입력해 주세요.")
                ))
                .toList();

        return response(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                errors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldViolation> errors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return response(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                errors
        );
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidParameterException(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBodyException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return response(
                ErrorCode.MALFORMED_REQUEST_BODY,
                ErrorCode.MALFORMED_REQUEST_BODY.defaultMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowedException(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return response(
                ErrorCode.METHOD_NOT_ALLOWED,
                ErrorCode.METHOD_NOT_ALLOWED.defaultMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFoundException(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                ErrorCode.RESOURCE_NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND.defaultMessage(),
                request,
                List.of()
        );
    }

    /**
     * TTS 스트리밍 응답을 쓰는 도중 클라이언트(프론트엔드)가 연결을 먼저 끊는 경우 — 예를 들어
     * 새 발화가 들어오면 VoicePanel.stopSpeaking()이 이전 TTS 요청을 의도적으로 abort() 한다.
     * 이건 정상 동작이라 ERROR로 남기면 진짜 에러가 로그에 묻힌다. 게다가 이미 끊긴 연결에
     * 에러 응답 본문을 다시 쓰려고 하면 똑같은 쓰기 실패가 한 번 더 나므로, 응답을 시도하지 않고
     * 조용히 넘어간다.
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, ClientAbortException.class})
    public void handleClientAbort(Exception exception, HttpServletRequest request) {
        log.debug("클라이언트가 응답을 받기 전에 연결을 끊었습니다: {}", request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        if (isClientAbort(exception)) {
            log.debug("클라이언트가 응답을 받기 전에 연결을 끊었습니다: {}", request.getRequestURI());
            return null;
        }

        log.error("Unhandled exception while processing {}", request.getRequestURI(), exception);

        return response(
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(),
                request,
                List.of()
        );
    }

    /** 예외 원인 체인을 따라가며 클라이언트가 연결을 먼저 끊어서 생긴 실패인지 확인한다. */
    private boolean isClientAbort(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ClientAbortException || current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ResponseEntity<ApiErrorResponse> response(
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<FieldViolation> errors
    ) {
        ApiErrorResponse body = ApiErrorResponse.of(
                errorCode,
                message,
                request.getRequestURI(),
                errors
        );

        return ResponseEntity.status(errorCode.status()).body(body);
    }
}
