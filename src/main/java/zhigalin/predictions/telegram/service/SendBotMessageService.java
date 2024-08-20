package zhigalin.predictions.telegram.service;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import zhigalin.predictions.telegram.model.EPLInfoBot;

@RequiredArgsConstructor
@Service
public class SendBotMessageService {

    private final EPLInfoBot bot;

    @SneakyThrows
    public void sendMessage(String chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);

        createKeyBoard(sendMessage);

        bot.execute(sendMessage);
    }

    public void createKeyBoard(SendMessage sendMessage) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace[3].getClassName().endsWith("TourCommand")) {
            InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> list = getButtonsList();
            keyBoard.setKeyboard(list);
            sendMessage.setReplyMarkup(keyBoard);
        } else {
            ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
            replyKeyboardRemove.setRemoveKeyboard(true);
            sendMessage.setReplyMarkup(replyKeyboardRemove);
        }
    }

    private static List<List<InlineKeyboardButton>> getButtonsList() {
        List<InlineKeyboardButton> listOfButtons = null;
        List<List<InlineKeyboardButton>> list = new ArrayList<>();
        for (int i = 0; i < 39; i++) {
            if (i % 8 == 0 || i == 38) {
                if (listOfButtons != null) {
                    list.add(listOfButtons);
                    if (i == 38) {
                        continue;
                    }
                }
                listOfButtons = new ArrayList<>();
            }
            InlineKeyboardButton button = new InlineKeyboardButton(String.valueOf(i + 1));
            button.setCallbackData("/tour" + (i + 1));
            listOfButtons.add(button);
        }
        return list;
    }
}
