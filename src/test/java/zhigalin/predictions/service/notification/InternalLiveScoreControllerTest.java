package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;

class InternalLiveScoreControllerTest {

    @Test
    void acceptsPlainLoopbackWithoutForwardedHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertTrue(InternalLiveScoreController.isDirectLoopback(request));
    }

    @Test
    void rejectsForwardedHeaderOnUnwrappedPeerEvenWhenWrapperHidesIt() {
        HttpServletRequest peer = mock(HttpServletRequest.class);
        when(peer.getRemoteAddr()).thenReturn("127.0.0.1");
        when(peer.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");
        HttpServletRequest wrapped = new HttpServletRequestWrapper(peer) {
            @Override
            public String getHeader(String name) {
                if ("X-Forwarded-For".equalsIgnoreCase(name) || "Forwarded".equalsIgnoreCase(name)) {
                    return null;
                }
                return super.getHeader(name);
            }

            @Override
            public String getRemoteAddr() {
                return "203.0.113.10";
            }
        };
        assertFalse(InternalLiveScoreController.isDirectLoopback(wrapped));
    }

    @Test
    void acceptsUnwrappedLoopbackWithoutForwardedHeaders() {
        HttpServletRequest peer = mock(HttpServletRequest.class);
        when(peer.getRemoteAddr()).thenReturn("127.0.0.1");
        HttpServletRequest wrapped = new HttpServletRequestWrapper(peer) {
            @Override
            public String getRemoteAddr() {
                return "203.0.113.10";
            }
        };
        assertTrue(InternalLiveScoreController.isDirectLoopback(wrapped));
    }

    @Test
    void rejectsNonLoopbackPeer() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("192.168.1.10");
        assertFalse(InternalLiveScoreController.isDirectLoopback(request));
    }
}
