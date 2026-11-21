package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.util.DaoUtil;

class CardLogosTest {

    private static final int TEAM_ID = 888_002;

    @BeforeEach
    void putTeam() {
        CardLogos.clearCacheForTests();
        DaoUtil.TEAMS.put(TEAM_ID, Team.builder()
                .id(TEAM_ID)
                .code("QQQ")
                .name("Unknown Club")
                .build());
    }

    @AfterEach
    void clear() {
        DaoUtil.TEAMS.remove(TEAM_ID);
        CardLogos.clearCacheForTests();
    }

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

    @Test
    void monogramIsSvgDataUriWithCode() {
        String uri = CardLogos.monogramDataUri("ips");
        assertTrue(uri.startsWith("data:image/svg+xml;base64,"));
        String decoded = new String(java.util.Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1)));
        assertTrue(decoded.contains("IPS"));
    }

    @Test
    void resolveFallsBackToMonogramWhenCodeKnownAndNoBundledLogo() {
        String src = CardLogos.resolveLogoSrc(TEAM_ID, null, "QQQ");
        assertTrue(src.startsWith("data:image/svg+xml;base64,"), src);
        String decoded = new String(java.util.Base64.getDecoder().decode(src.substring(src.indexOf(',') + 1)));
        assertTrue(decoded.contains("QQQ"));
    }

    @Test
    void resolveUsesBundledByCodeForIpswich() {
        String ips = CardLogos.resolveLogoSrc(null, null, "IPS");
        String bri = CardLogos.resolveLogoSrc(null, null, "BRI");
        String hul = CardLogos.resolveLogoSrc(null, null, "HUL");
        String eve = CardLogos.resolveLogoSrc(null, null, "EVE");
        assertTrue(ips.startsWith("data:image/png;base64,"), ips);
        assertTrue(bri.startsWith("data:image/png;base64,"), bri);
        assertTrue(hul.startsWith("data:image/png;base64,"), hul);
        assertTrue(eve.startsWith("data:image/png;base64,"), eve);
        assertTrue(ips.length() > 200, "bundled IPS crest should be present");
        assertTrue(!ips.equals(bri), "IPS must not reuse the Brighton crest bytes");
        assertTrue(!hul.equals(eve), "HUL must not reuse the Everton crest bytes");
    }

    @Test
    void resolveUsesCodeHintWhenTeamIdUnknown() {
        String src = CardLogos.resolveLogoSrc(null, null, "ZZZ");
        assertTrue(src.startsWith("data:image/svg+xml;base64,"));
        String decoded = new String(java.util.Base64.getDecoder().decode(src.substring(src.indexOf(',') + 1)));
        assertTrue(decoded.contains("ZZZ"));
    }
}
