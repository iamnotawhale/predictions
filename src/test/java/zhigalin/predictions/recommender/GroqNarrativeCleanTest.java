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
        String raw = "Лирика.\n\nФорма дома 2.10.\n\nВероятный счёт 2:1 в пользу хозяев по дому.";
        String cleaned = GroqNarrativeClient.cleanNarrative(raw);
        assertNotNull(cleaned);
        assertEquals(3, cleaned.split("\\n\\s*\\n").length);
        assertTrue(cleaned.contains("2:1"));
    }

    @Test
    void rejectsConclusionThatRepeatsStatDigits() {
        String raw = "Лирика.\n\nФорма: xG 1.26.\n\nСчёт 2:1, xG 1.26 против 1.25 и 1 очко против 0.60.";
        assertTrue(GroqNarrativeClient.conclusionHasStatDigits(raw));
        assertEquals(null, GroqNarrativeClient.cleanNarrative(raw));
    }

    @Test
    void allowsTipScoreOnlyInConclusion() {
        String raw = "Лирика.\n\nФорма: xG 1.26 и 0.60 очков.\n\nСкорее всего 2:1 — хозяева стабильнее дома.";
        assertFalse(GroqNarrativeClient.conclusionHasStatDigits(raw));
        assertNotNull(GroqNarrativeClient.cleanNarrative(raw));
    }

    @Test
    void rejectsSecondScorelineInConclusion() {
        String raw = "Лирика.\n\nФорма.\n\nСчёт 2:1, хотя 1:0 тоже возможен.";
        assertTrue(GroqNarrativeClient.conclusionHasStatDigits(raw));
        assertEquals(null, GroqNarrativeClient.cleanNarrative(raw));
    }

    @Test
    void rejectsStatDigitsWhenTrimDropsMiddleParagraph() {
        String intro = "А".repeat(700) + ".";
        String close = "Скорее всего 2:1 при xG 1.26 против 1.25.";
        String raw = intro + "\n\n" + "Б".repeat(800) + ".\n\n" + close;
        assertTrue(GroqNarrativeClient.conclusionHasStatDigits(raw));
        assertEquals(null, GroqNarrativeClient.cleanNarrative(raw));
    }

    @Test
    void rejectsStatDigitsOnLaterSingleNewlineLine() {
        String raw = "Лирика.\nЕщё.\nБез цифр.\nСчёт 2:1, xG 1.26.";
        assertTrue(GroqNarrativeClient.conclusionHasStatDigits(raw));
        assertEquals(null, GroqNarrativeClient.cleanNarrative(raw));
    }

    @Test
    void allowsSingleNewlineThreeLineTipOnly() {
        String raw = "Лирика.\nФорма без цифр здесь.\nСчёт 2:1.";
        assertFalse(GroqNarrativeClient.conclusionHasStatDigits(raw));
    }
}
