package com.sms.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.sms.common.FileStorage;
import com.sms.common.FormException;
import com.sms.common.InUseException;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Every API error has the same JSON shape. Scoped to the API controllers, so the website keeps
 * its own error pages (and Spring Security still handles its access errors).
 */
@RestControllerAdvice(basePackages = "com.sms.api")
public class ApiErrors {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    /** fieldErrors maps a request field to its message; empty when the error isn't about one field. */
    public record ApiError(int status, String error, String message, Map<String, String> fieldErrors) {
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ApiError> invalid(BindException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        String message = e.getGlobalErrors().isEmpty() ? "Check the highlighted fields."
                : e.getGlobalErrors().getFirst().getDefaultMessage();
        return body(HttpStatus.BAD_REQUEST, message, fields);
    }

    @ExceptionHandler(FormException.class)
    ResponseEntity<ApiError> form(FormException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(),
                e.getField() == null ? Map.of() : Map.of(e.getField(), e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    ResponseEntity<ApiError> unreadable(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "The request couldn't be read. Check the values sent.", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthorized(AuthenticationException e) {
        return body(HttpStatus.UNAUTHORIZED, e.getMessage(), Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "You don't have access to this.", Map.of());
    }

    @ExceptionHandler(InUseException.class)
    ResponseEntity<ApiError> inUse(InUseException e) {
        return body(HttpStatus.CONFLICT, "This record is still in use, so it can't be deleted.", Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.CONTENT_TOO_LARGE,
                "File is too large (max " + FileStorage.MAX_BYTES / (1024 * 1024) + " MB)", Map.of());
    }

    /** Spring's own web errors (404 not found, 405, missing parameter, ...) keep their status; anything else is a 500. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> other(Exception e) {
        if (e instanceof ErrorResponse web) {
            String detail = web.getBody().getDetail();
            return body(web.getStatusCode(), detail == null ? "Request failed." : detail, Map.of());
        }
        log.error("Unexpected API error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server.", Map.of());
    }

    /** Same shape, written directly; used by the security chain for 401 and 403. */
    public static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message + "\",\"fieldErrors\":{}}");
    }

    private static ResponseEntity<ApiError> body(HttpStatusCode code, String message, Map<String, String> fields) {
        HttpStatus status = HttpStatus.resolve(code.value());
        String reason = status == null ? "Error" : status.getReasonPhrase();
        return ResponseEntity.status(code).body(new ApiError(code.value(), reason, message, fields));
    }
}
