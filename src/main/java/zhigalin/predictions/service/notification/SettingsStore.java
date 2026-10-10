package zhigalin.predictions.service.notification;

import java.util.Optional;

/** Tiny key/value persistence for runtime-switchable settings. */
public interface SettingsStore {

    Optional<String> get(String key);

    void put(String key, String value);
}
