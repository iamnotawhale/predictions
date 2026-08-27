package zhigalin.predictions.telegram.command;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.panic.PanicSender;
import zhigalin.predictions.service.notification.NotificationService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

@RequiredArgsConstructor
public class TodayNotifyCommand implements Command {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{8})\\b");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SendBotMessageService messageService;
    private final NotificationService notificationService;
    private final PanicSender panicSender;
    private final long adminChatId;

    @Override
    public void execute(Update update) {
        if (update.getMessage().getChatId() != adminChatId) {
            return;
        }
        String chatId = String.valueOf(adminChatId);
        try {
            LocalDate date = parseDate(update.getMessage().getText());
            if (notificationService.sendTodayMatchNotification(date)) {
                messageService.sendMessage(
                        chatId,
                        "Отправлено уведомление о матчах на " + DISPLAY_FORMAT.format(date) + " в общий чат"
                );
            } else {
                messageService.sendMessage(
                        chatId,
                        "На " + DISPLAY_FORMAT.format(date) + " матчей нет или не удалось сформировать картинку"
                );
            }
        } catch (DateTimeParseException e) {
            messageService.sendMessage(
                    chatId,
                    "Неверная дата. Формат: todaypub 17052026 (ДДММГГГГ)"
            );
        } catch (Exception e) {
            panicSender.sendPanic("Can't send today matches notification", e);
            messageService.sendMessage(chatId, "Ошибка отправки: " + e.getMessage());
        }
    }

    private static LocalDate parseDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            return LocalDate.parse(matcher.group(1), DATE_FORMAT);
        }
        return LocalDate.now();
    }
}
