package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GroqNarrativeCleanTest {

    @Test
    void detectsAndRejectsChineseGlyphInScore() {
        String raw = "Лирика.\n\nФорма.\n\nВероятный счёт 2比1.";
        assertTrue(GroqNarrativeClient.hasCjk(raw));
        assertEquals(null, GroqNarrativeClient.cleanNarrative(raw));
        assertFalse(GroqNarrativeClient.hasCjk(GroqNarrativeClient.stripCjk(raw)));
    }

    @Test
    void cleanNarrativeKeepsRussianParagraphs() {
        String raw = "Лирика.\n\nФорма дома 2.10.\n\nВероятный счёт 2:1.";
        String cleaned = GroqNarrativeClient.cleanNarrative(raw);
        assertNotNull(cleaned);
        assertEquals(3, cleaned.split("\\n\\s*\\n").length);
        assertTrue(cleaned.contains("2:1"));
    }
}
