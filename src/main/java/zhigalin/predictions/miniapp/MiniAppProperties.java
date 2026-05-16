package zhigalin.predictions.miniapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "miniapp")
public class MiniAppProperties {

    private boolean devMode;
    private String devTelegramId;

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public String getDevTelegramId() {
        return devTelegramId;
    }

    public void setDevTelegramId(String devTelegramId) {
        this.devTelegramId = devTelegramId;
    }
}
