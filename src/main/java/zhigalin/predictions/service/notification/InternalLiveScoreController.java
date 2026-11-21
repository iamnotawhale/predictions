package zhigalin.predictions.service.notification;

import java.util.Map;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/live-score")
public class InternalLiveScoreController {

    private final NotificationService notificationService;

    public InternalLiveScoreController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/matches/{publicId}/refresh-goal")
    public Map<String, Object> refreshGoal(
            @PathVariable int publicId,
            HttpServletRequest request
    ) {
        if (!isDirectLoopback(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Integer messageId = notificationService.refreshLiveScoreGoalAnimation(publicId);
        if (messageId == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "refresh failed");
        }
        return Map.of("matchId", publicId, "messageId", messageId);
    }

    static boolean isDirectLoopback(HttpServletRequest request) {
        HttpServletRequest peer = unwrap(request);
        if (hasHeader(peer, "Forwarded") || hasHeader(peer, "X-Forwarded-For")) {
            return false;
        }
        String addr = peer.getRemoteAddr();
        return "127.0.0.1".equals(addr)
                || "::1".equals(addr)
                || "0:0:0:0:0:0:0:1".equals(addr);
    }

    private static boolean hasHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null && !value.isBlank();
    }

    private static HttpServletRequest unwrap(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof HttpServletRequestWrapper wrapper) {
            current = wrapper.getRequest();
        }
        return (HttpServletRequest) current;
    }
}
