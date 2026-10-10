package zhigalin.predictions.telegram;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.service.notification.CardStyleSettings;
import zhigalin.predictions.telegram.command.CardStyleCommand;
import zhigalin.predictions.telegram.service.SendBotMessageService;

class CardStyleCommandTest {

    private static final long ADMIN = 42L;

    private final SendBotMessageService messages = mock(SendBotMessageService.class);
    private final CardStyleSettings settings = mock(CardStyleSettings.class);
    private final CardStyleCommand command = new CardStyleCommand(messages, settings, ADMIN);

    private static Update update(long chatId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getText()).thenReturn(text);
        return update;
    }

    private String lastReply() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(messages).sendMessage(org.mockito.ArgumentMatchers.eq(String.valueOf(ADMIN)), text.capture());
        return text.getValue();
    }

    @Test
    void ignoresEveryoneButTheAdmin() {
        command.execute(update(7L, "/cardstyle paper"));
        verify(messages, never()).sendMessage(anyString(), anyString());
        verify(settings, never()).set(anyString());
    }

    @Test
    void withoutArgumentShowsCurrentAndChoices() {
        when(settings.current()).thenReturn("broadcast");
        when(settings.available()).thenReturn(List.of("classic", "broadcast"));
        command.execute(update(ADMIN, "/cardstyle"));
        String reply = lastReply();
        assertTrue(reply.contains("broadcast"));
        assertTrue(reply.contains("classic, broadcast"));
    }

    @Test
    void switchesToTheRequestedStyle() {
        when(settings.set("classic")).thenReturn(true);
        when(settings.current()).thenReturn("classic");
        when(settings.available()).thenReturn(List.of("classic", "broadcast"));
        command.execute(update(ADMIN, "/cardstyle classic"));
        verify(settings).set("classic");
        assertTrue(lastReply().contains("переключён: classic"));
    }

    @Test
    void rejectsUnknownStyles() {
        when(settings.set("neon")).thenReturn(false);
        when(settings.current()).thenReturn("broadcast");
        when(settings.available()).thenReturn(List.of("classic", "broadcast"));
        command.execute(update(ADMIN, "/cardstyle neon"));
        assertTrue(lastReply().contains("Неизвестный стиль «neon»"));
    }
}
