package com.mallota.dto.response;

import com.mallota.exception.ErrorCode;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> errors
) {

    public ApiErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            String path,
            List<FieldViolation> errors
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                errorCode.status().value(),
                errorCode.name(),
                message,
                path,
                errors
        );
    }
}
