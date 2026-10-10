package zhigalin.predictions.service.notification;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Which look the notification cards use. The choice survives restarts (stored in {@link SettingsStore})
 * and can be changed at runtime; {@code notification.cards.style} only provides the first value.
 */
@Component
public class CardStyleSettings {
    private static final Logger log = LoggerFactory.getLogger("server");
    static final String KEY = "notification.cards.style";

    private final SettingsStore store;

    public CardStyleSettings(SettingsStore store, @Value("${notification.cards.style:broadcast}") String configured) {
        this.store = store;
        String stored = null;
        try {
            stored = store.get(KEY).orElse(null);
        } catch (Exception e) {
            log.warn("Card style: cannot read stored value, using configured default: {}", e.getMessage());
        }
        String initial = isKnown(stored) ? stored : (isKnown(configured) ? configured : CardHtml.DEFAULT_STYLE);
        CardHtml.useStyle(initial);
        log.info("Card style: {}", CardHtml.style());
    }

    public String current() {
        return CardHtml.style();
    }

    public List<String> available() {
        return CardHtml.STYLES;
    }

    /** @return false (and no change) when {@code name} is not a known style */
    public boolean set(String name) {
        if (!isKnown(name)) {
            return false;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        CardHtml.useStyle(normalized);
        try {
            store.put(KEY, normalized);
        } catch (Exception e) {
            log.warn("Card style: applied but not persisted: {}", e.getMessage());
        }
        return true;
    }

    private static boolean isKnown(String name) {
        return name != null && CardHtml.STYLES.contains(name.trim().toLowerCase(Locale.ROOT));
    }
}
