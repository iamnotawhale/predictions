package zhigalin.predictions.miniapp;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.ActionResponse;

@RestControllerAdvice(assignableTypes = MiniAppController.class)
public class MiniAppExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger("server");

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ActionResponse> handleMultipart(MultipartException ex, HttpServletRequest request) {
        log.warn("Broken multipart request: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActionResponse(false, "Некорректный запрос. Откройте mini app и сохраните прогноз ещё раз."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ActionResponse> handleUnsupportedMedia(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        log.warn("Unsupported media type: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActionResponse(false, "Некорректный формат запроса. Обновите mini app и попробуйте снова."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ActionResponse> handleUnreadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("Unreadable body: {} {} — {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ActionResponse(false, "Некорректные данные запроса. Попробуйте сохранить прогноз ещё раз."));
    }
}
