package zhigalin.predictions.recommender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GroqNarrativeTrimTest {

    @Test
    void keepsThreeParagraphsAndClosingScore() {
        String longForm = "Форма. ".repeat(200);
        String raw = "Лирика матча.\n\n" + longForm + "\n\nВероятный счёт 2:1 в пользу хозяев.";
        String trimmed = GroqNarrativeClient.trimToThreeParagraphs(raw);
        String[] paras = trimmed.split("\\n\\s*\\n");
        assertEquals(3, paras.length);
        assertTrue(trimmed.endsWith("Вероятный счёт 2:1 в пользу хозяев."));
        assertTrue(trimmed.length() <= 1_400);
    }

    @Test
    void singleBlockKeepsClosingScoreAtTail() {
        String raw = "Ввод. ".repeat(300) + "Вероятный счёт 2:1 в пользу хозяев.";
        String trimmed = GroqNarrativeClient.trimToThreeParagraphs(raw);
        assertTrue(trimmed.length() <= 1_400);
        assertTrue(trimmed.contains("Вероятный счёт 2:1 в пользу хозяев."));
    }
}
