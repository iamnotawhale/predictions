package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GroqNarrativeCleanTest {

    @Test
    void stripsChineseGlyphAndKeepsRussian() {
        String raw = "Лирика.\n\nФорма.\n\nВероятный счёт 2比1.";
        assertTrue(GroqNarrativeClient.hasCjk(raw));
        String cleaned = GroqNarrativeClient.cleanNarrative(raw);
        assertNotNull(cleaned);
        assertFalse(GroqNarrativeClient.hasCjk(cleaned));
        assertTrue(cleaned.contains("Лирика"));
        assertTrue(cleaned.contains("2") && cleaned.contains("1"));
    }

    @Test
    void cleanNarrativeKeepsRussianParagraphs() {
        String raw = "Лирика.\n\nФорма дома 2.10.\n\nВероятный счёт 2:1 в пользу хозяев по дому.";
        String cleaned = GroqNarrativeClient.cleanNarrative(raw);
        assertNotNull(cleaned);
        assertEquals(3, cleaned.split("\\n\\s*\\n").length);
        assertTrue(cleaned.contains("2:1"));
    }

    @Test
    void keepsConclusionEvenWithRepeatedStatDigits() {
        String raw = "Лирика.\n\nФорма: xG 1.26.\n\nСчёт 2:1, xG 1.26 против 1.25 и 1 очко против 0.60.";
        String cleaned = GroqNarrativeClient.cleanNarrative(raw);
        assertNotNull(cleaned);
        assertTrue(cleaned.contains("1.26"));
        assertTrue(cleaned.contains("2:1"));
        assertTrue(cleaned.contains("0.60"));
    }
}
