package zhigalin.predictions.service.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CardStyleSettingsTest {

    private static final class MemoryStore implements SettingsStore {
        final Map<String, String> data = new HashMap<>();
        boolean failReads;
        boolean failWrites;

        @Override
        public Optional<String> get(String key) {
            if (failReads) {
                throw new IllegalStateException("db down");
            }
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public void put(String key, String value) {
            if (failWrites) {
                throw new IllegalStateException("db down");
            }
            data.put(key, value);
        }
    }

    @AfterEach
    void restore() {
        CardHtml.useStyle(CardHtml.DEFAULT_STYLE);
    }

    @Test
    void emptyStoreUsesConfiguredStyle() {
        CardStyleSettings s = new CardStyleSettings(new MemoryStore(), "paper");
        assertEquals("paper", s.current());
        assertEquals("paper", CardHtml.style());
    }

    @Test
    void unknownConfiguredStyleFallsBackToBroadcast() {
        assertEquals("broadcast", new CardStyleSettings(new MemoryStore(), "neon").current());
        assertEquals("broadcast", new CardStyleSettings(new MemoryStore(), null).current());
    }

    @Test
    void storedChoiceBeatsConfiguredDefault() {
        MemoryStore store = new MemoryStore();
        store.data.put(CardStyleSettings.KEY, "classic");
        assertEquals("classic", new CardStyleSettings(store, "broadcast").current());
    }

    @Test
    void invalidStoredValueIsIgnored() {
        MemoryStore store = new MemoryStore();
        store.data.put(CardStyleSettings.KEY, "garbage");
        assertEquals("pitch", new CardStyleSettings(store, "pitch").current());
    }

    @Test
    void setAppliesAndPersistsAndSurvivesRestart() {
        MemoryStore store = new MemoryStore();
        CardStyleSettings s = new CardStyleSettings(store, "broadcast");
        assertTrue(s.set("  Classic "));
        assertEquals("classic", CardHtml.style());
        assertEquals("classic", store.data.get(CardStyleSettings.KEY));
        assertEquals("classic", new CardStyleSettings(store, "broadcast").current());
    }

    @Test
    void unknownStyleIsRejectedWithoutChange() {
        MemoryStore store = new MemoryStore();
        CardStyleSettings s = new CardStyleSettings(store, "paper");
        assertFalse(s.set("neon"));
        assertFalse(s.set(null));
        assertEquals("paper", s.current());
        assertFalse(store.data.containsKey(CardStyleSettings.KEY));
    }

    @Test
    void databaseFailuresDoNotBreakRendering() {
        MemoryStore store = new MemoryStore();
        store.failReads = true;
        CardStyleSettings s = new CardStyleSettings(store, "paper");
        assertEquals("paper", s.current());
        store.failWrites = true;
        assertTrue(s.set("pitch"), "style still switches in memory when persisting fails");
        assertEquals("pitch", s.current());
    }

    @Test
    void listsAllStyles() {
        assertEquals(CardHtml.STYLES, new CardStyleSettings(new MemoryStore(), "broadcast").available());
    }
}
