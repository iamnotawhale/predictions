package zhigalin.predictions.miniapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import zhigalin.predictions.telegram.model.EPLInfoBot;

@Component
public class MiniAppMenuConfigurer {

    private static final Logger log = LoggerFactory.getLogger("server");

    private final EPLInfoBot bot;
    private final String webAppUrl;

    public MiniAppMenuConfigurer(EPLInfoBot bot, @Value("${bot.webAppUrl:}") String webAppUrl) {
        this.bot = bot;
        this.webAppUrl = webAppUrl;
    }

    @Order(200)
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (webAppUrl.isBlank() || !webAppUrl.startsWith("https://")) {
            log.warn("bot.webAppUrl must be a public HTTPS URL; menu button not set ({})", webAppUrl);
            return;
        }
        try {
            WebAppInfo webAppInfo = WebAppInfo.builder().url(webAppUrl).build();
            MenuButtonWebApp menuButton = MenuButtonWebApp.builder()
                    .text("Открыть приложение")
                    .webAppInfo(webAppInfo)
                    .build();
            bot.execute(SetChatMenuButton.builder().menuButton(menuButton).build());
            log.info("Telegram Mini App menu button set: {}", webAppUrl);
        } catch (Exception e) {
            log.error("Failed to set Mini App menu button (bot keeps running)", e);
        }
    }
}
