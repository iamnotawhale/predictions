package zhigalin.predictions.miniapp.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.service.user.UserService;

@Component
public class AdminGuard {

    private final UserService userService;
    private final String adminChatId;

    public AdminGuard(
            UserService userService,
            @Value("${chatId:}") String adminChatId
    ) {
        this.userService = userService;
        this.adminChatId = adminChatId == null ? "" : adminChatId.trim();
    }

    public void requireAdmin(String telegramId) {
        if (userService.findByTelegramId(telegramId) == null) {
            throw new MiniAppException(401, "Пользователь не найден.");
        }
        if (adminChatId.isBlank() || "0".equals(adminChatId) || !adminChatId.equals(telegramId)) {
            throw new MiniAppException(403, "Доступ только для администратора.");
        }
    }
}
