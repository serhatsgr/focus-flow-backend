package com.serhatsgr.focusflow_backend.handler;

import tools.jackson.databind.exc.InvalidFormatException;
import com.serhatsgr.focusflow_backend.exception.BaseException;
import com.serhatsgr.focusflow_backend.exception.ErrorMessage;
import com.serhatsgr.focusflow_backend.exception.MessageType;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * FocusFlow Global Exception Handler.
 *
 * Değişiklik:
 * - import tools.jackson... → import com.fasterxml.jackson... (DÜZELTME: kritik
 * bug)
 * Önceki hata: Jackson format hata yakalama hiç çalışmıyordu.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageSource messageSource;

    @Autowired
    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helper Metotlar
    // ──────────────────────────────────────────────────────────────────────────

    private String resolveTraceId(HttpServletRequest request) {
        String trace = request != null ? request.getHeader("X-Trace-Id") : null;
        return (trace != null && !trace.isBlank()) ? trace : UUID.randomUUID().toString();
    }

    private String getLocalizedMessage(String code, Object... args) {
        try {
            return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
        } catch (Exception ex) {
            return code;
        }
    }

    private ApiError buildApiError(HttpStatus status, String message, String code,
            String path, String traceId, Object details) {
        ApiError apiError = new ApiError();
        apiError.setStatus(status.value());
        apiError.setError(status.getReasonPhrase());
        apiError.setCode(code);
        apiError.setMessage(message);
        apiError.setTimestamp(LocalDateTime.now());
        apiError.setPath(path);
        apiError.setTraceId(traceId);
        apiError.setDetails(details);
        return apiError;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Handler'lar
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * 1. JSON Format Hataları (tarih, sayı, enum vb.)
     * com.fasterxml.jackson.databind.exc.InvalidFormatException kullanılıyor.
     * (Önceki kod tools.jackson paketini import ediyordu — hiç çalışmıyordu.)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        String message = "Girdiğiniz verilerde format hatası var.";

        if (ex.getCause() instanceof InvalidFormatException ife) {
            if (ife.getTargetType().equals(LocalDate.class)) {
                message = "Geçersiz bir tarih girdiniz. Lütfen (Yıl-Ay-Gün) formatında giriniz.";
            } else if (Number.class.isAssignableFrom(ife.getTargetType())
                    || ife.getTargetType().equals(int.class)
                    || ife.getTargetType().equals(long.class)) {
                message = "Bu alana sadece sayısal değer girebilirsiniz.";
            } else if (Enum.class.isAssignableFrom(ife.getTargetType())) {
                message = "Seçtiğiniz değer geçerli seçenekler arasında değil.";
            }
        }

        log.warn("JSON Parse hatası: {}", ex.getMessage());

        return ResponseEntity.badRequest().body(
                buildApiError(HttpStatus.BAD_REQUEST, message,
                        MessageType.VALIDATION_ERROR.getCode(),
                        request.getRequestURI(), resolveTraceId(request), null));
    }

    /**
     * 2. BaseException — Özel iş kuralı istisnaları
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBaseException(BaseException ex, HttpServletRequest request) {
        ErrorMessage em = ex.getErrorMessage();
        MessageType mt = em != null ? em.getMessageType() : MessageType.GENERAL_EXCEPTION;
        HttpStatus status = mt != null ? mt.getHttpStatus() : HttpStatus.BAD_REQUEST;
        String code = mt != null ? mt.getCode() : "9999";

        String baseMessage = mt != null ? getLocalizedMessage(mt.getMessageKey()) : "Hata oluştu";
        String finalMessage = (em != null && em.getOfStatic() != null) ? em.getOfStatic() : baseMessage;

        return ResponseEntity.status(status).body(
                buildApiError(status, finalMessage, code,
                        request.getRequestURI(), resolveTraceId(request), null));
    }

    /**
     * 3. Validation Hataları (@Valid / @Validated)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String errorMessage = ex.getBindingResult().getFieldErrors().isEmpty()
                ? "Validasyon hatası"
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();

        Map<String, List<String>> details = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        fe -> fe.getField(),
                        Collectors.mapping(fe -> fe.getDefaultMessage(), Collectors.toList())));

        return ResponseEntity.badRequest().body(
                buildApiError(HttpStatus.BAD_REQUEST, errorMessage,
                        MessageType.VALIDATION_ERROR.getCode(),
                        request.getRequestURI(), resolveTraceId(request), details));
    }

    /**
     * 4. Constraint Violation (@Validated metot parametresi)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("Validasyon hatası");

        return ResponseEntity.badRequest().body(
                buildApiError(HttpStatus.BAD_REQUEST, message,
                        MessageType.VALIDATION_ERROR.getCode(),
                        request.getRequestURI(), resolveTraceId(request), null));
    }

    /**
     * 5. Yol / Parametre Tipi Uyumsuzluğu
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(
                buildApiError(HttpStatus.BAD_REQUEST, "Girdiğiniz parametre formatı hatalı.",
                        MessageType.VALIDATION_ERROR.getCode(),
                        request.getRequestURI(), resolveTraceId(request), null));
    }

    /**
     * 6. Güvenlik Hataları
     * NOT: AccessDeniedException Spring Security filter zincirinde yakalanır;
     * 
     * @ControllerAdvice'a düşmeyebilir. JwtAuthFilter'da handlerExceptionResolver
     *                     bu handler'ı çağıracak şekilde konfigüre edilmiştir.
     */
    @ExceptionHandler({ AuthenticationException.class, JwtException.class,
            ExpiredJwtException.class, AccessDeniedException.class })
    public ResponseEntity<ApiError> handleSecurityExceptions(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String message = "Giriş yapmanız gerekiyor.";
        String code = MessageType.UNAUTHORIZED.getCode();

        if (ex instanceof AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
            message = "Bu işlem için yetkiniz yok.";
            code = MessageType.FORBIDDEN.getCode();
        } else if (ex instanceof ExpiredJwtException) {
            message = "Oturum süreniz doldu.";
            code = MessageType.TOKEN_EXPIRED.getCode();
        } else if (ex instanceof DisabledException) {
            message = "Hesabınız erişime kapatılmıştır.";
            code = "ACCOUNT_DISABLED";
        }

        return ResponseEntity.status(status).body(
                buildApiError(status, message, code,
                        request.getRequestURI(), resolveTraceId(request), null));
    }

    /**
     * 7. Beklenmeyen Tüm Diğer Hatalar
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex, HttpServletRequest request) {
        log.error("Beklenmeyen hata: ", ex);
        return ResponseEntity.internalServerError().body(
                buildApiError(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Sunucuda geçici bir sorun oluştu.",
                        MessageType.INTERNAL_ERROR.getCode(),
                        request.getRequestURI(), resolveTraceId(request), null));
    }
}