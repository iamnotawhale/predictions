package zhigalin.predictions.telegram.service;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
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

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendMessageDeletingKeyboard(Integer messageId, String chatId, String message) {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageId);
        bot.execute(deleteMessage);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendPredictKeyBoard(String chatId, String homeTeam, String awayTeam) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText("Выбери прогноз");

        createPredictKeyBoard(sendMessage, homeTeam, awayTeam);
        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendTourKeyBoard(String chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);

        createTourKeyBoard(sendMessage);
        bot.execute(sendMessage);
    }

    private void createPredictKeyBoard(SendMessage sendMessage, String homeTeam, String awayTeam) {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboard = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            List<InlineKeyboardButton> innerList = new ArrayList<>();
            for (int j = 0; j < 6; j++) {
                InlineKeyboardButton button = new InlineKeyboardButton(j + ":" + i);
                button.setCallbackData("/pred " + homeTeam + " " + j + " " + awayTeam + " " + i);
                innerList.add(button);
            }
            listOfKeyboard.add(innerList);
        }

        keyBoard.setKeyboard(listOfKeyboard);
        sendMessage.setReplyMarkup(keyBoard);
    }

    private static void createTourKeyBoard(SendMessage sendMessage) {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboardRows = new ArrayList<>();
        int rows = 5;
        int columns = 8;
        for (int i = 0; i < rows; i++) {
            List<InlineKeyboardButton> innerList = new ArrayList<>();
            for (int j = 0; j < columns; j++) {
                int tour = i * columns + j + 1;
                if (tour > 38) {
                    continue;
                }
                InlineKeyboardButton button = new InlineKeyboardButton(String.valueOf(tour));
                button.setCallbackData("/tour" + tour);
                innerList.add(button);
            }
            listOfKeyboardRows.add(innerList);
        }
        keyBoard.setKeyboard(listOfKeyboardRows);
        sendMessage.setReplyMarkup(keyBoard);
    }
}
