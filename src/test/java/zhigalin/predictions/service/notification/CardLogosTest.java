package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CardLogosTest {

    @Test
    void embedPassesThroughNonHttpInputs() {
        assertEquals("", CardLogos.embedRemoteLogo(null));
        assertEquals("", CardLogos.embedRemoteLogo("  "));
        assertEquals("data:image/png;base64,AA", CardLogos.embedRemoteLogo("data:image/png;base64,AA"));
        assertEquals("/img/x.png", CardLogos.embedRemoteLogo("/img/x.png"));
    }

    @Test
    void resolveWithoutTeamIdOrUrlIsEmpty() {
        assertEquals("", CardLogos.resolveLogoSrc(null, null));
        assertEquals("", CardLogos.resolveLogoSrc(null, " "));
        assertEquals("data:image/png;base64,AA", CardLogos.resolveLogoSrc(null, "data:image/png;base64,AA"));
    }

    @Test
    void mimeComesFromHeaderThenExtensionThenDefault() {
        assertEquals("image/webp", CardLogos.contentTypeToMime("image/webp; charset=x", "http://a/b"));
        assertEquals("image/webp", CardLogos.contentTypeToMime("text/html", "http://a/b.WEBP?x=1"));
        assertEquals("image/svg+xml", CardLogos.contentTypeToMime(null, "http://a/b.svg"));
        assertEquals("image/png", CardLogos.contentTypeToMime("", "http://a/b"));
    }
}
