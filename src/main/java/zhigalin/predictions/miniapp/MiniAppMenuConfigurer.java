package zhigalin.predictions.miniapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import zhigalin.predictions.telegram.model.EPLInfoBot;

@Component
@ConditionalOnProperty(name = "bot.webAppUrl")
public class MiniAppMenuConfigurer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final EPLInfoBot bot;
    private final String webAppUrl;

    public MiniAppMenuConfigurer(EPLInfoBot bot, @Value("${bot.webAppUrl}") String webAppUrl) {
        this.bot = bot;
        this.webAppUrl = webAppUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (webAppUrl.isBlank() || !webAppUrl.startsWith("https://")) {
            log.warn("bot.webAppUrl must be a public HTTPS URL; menu button not set ({})", webAppUrl);
            return;
        }
        try {
            WebAppInfo webAppInfo = WebAppInfo.builder().url(webAppUrl).build();
            MenuButtonWebApp menuButton = MenuButtonWebApp.builder()
                    .text("Открыть")
                    .build();
            menuButton.setWebAppInfo(webAppInfo);
            bot.execute(SetChatMenuButton.builder().menuButton(menuButton).build());
            log.info("Telegram Mini App menu button set: {}", webAppUrl);
        } catch (TelegramApiException e) {
            log.error("Failed to set Mini App menu button", e);
        }
    }
}
