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
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.telegram.model.EPLInfoBot;
import zhigalin.predictions.util.DaoUtil;

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
    public void sendMessageDeletingKeyboard(Integer deleteMessageId, String chatId, String message) {
        deletePreviousMessage(deleteMessageId, chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendPredictKeyBoard(String chatId, String message, String homeTeam, String awayTeam) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createPredictKeyBoard(homeTeam, awayTeam));

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendTourKeyBoard(String chatId, String message, String prefix) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createTourKeyBoard(prefix));

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendWeeklyPredictsByUserKeyBoard(Integer deleteMessageId, String chatId, String message, List<MatchPrediction> matchPredictions) {
        deletePreviousMessage(deleteMessageId, chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createPredictTourUsersKeyBoard(matchPredictions));

        bot.execute(sendMessage);
    }


    @SneakyThrows
    public void sendMessageDeletingKeyboardTourMatches(Integer messageIdToDelete, List<Match> tourMatches, String chatId, String message) {
        deletePreviousMessage(messageIdToDelete, chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createMatchesKeyBoard(tourMatches));

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendMessageWithMatchesKeyboard(List<Match> matches, String chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createMatchesKeyBoard(matches));

        bot.execute(sendMessage);
    }

    private static InlineKeyboardMarkup createPredictKeyBoard(String homeTeam, String awayTeam) {
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
        return keyBoard;
    }

    private static InlineKeyboardMarkup createTourKeyBoard(String prefix) {
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
                button.setCallbackData("/" + prefix + tour);
                innerList.add(button);
            }
            listOfKeyboardRows.add(innerList);
        }
        keyBoard.setKeyboard(listOfKeyboardRows);
        return keyBoard;
    }

    private static InlineKeyboardMarkup createMatchesKeyBoard(List<Match> matches) {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboardRows = new ArrayList<>();
        int matchNum = 1;
        List<InlineKeyboardButton> innerList = new ArrayList<>();
        for (Match match : matches) {
            if (matchNum > 1 && matchNum % 2 == 1) {
                listOfKeyboardRows.add(innerList);
                innerList = new ArrayList<>();
            }
            String homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
            String awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
            InlineKeyboardButton button = new InlineKeyboardButton(String.join("-", homeTeam, awayTeam));
            button.setCallbackData("/" + homeTeam + ":" + awayTeam);
            innerList.add(button);
            if (matchNum == matches.size()) {
                listOfKeyboardRows.add(innerList);
            }
            matchNum++;
        }
        keyBoard.setKeyboard(listOfKeyboardRows);
        return keyBoard;
    }

    private static InlineKeyboardMarkup createPredictTourUsersKeyBoard(List<MatchPrediction> matchPredictions) {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboardRows = new ArrayList<>();
        int predictNum = 1;
        List<InlineKeyboardButton> innerList = new ArrayList<>();
        for (MatchPrediction matchPrediction : matchPredictions) {
            if (predictNum > 1 && predictNum % 2 == 1) {
                listOfKeyboardRows.add(innerList);
                innerList = new ArrayList<>();
            }
            String homeTeam = DaoUtil.TEAMS.get(matchPrediction.match().getHomeTeamId()).getCode();
            String awayTeam = DaoUtil.TEAMS.get(matchPrediction.match().getAwayTeamId()).getCode();

            String homeTeamScore = String.valueOf(matchPrediction.prediction().getHomeTeamScore());
            String awayTeamScore = String.valueOf(matchPrediction.prediction().getAwayTeamScore());

            InlineKeyboardButton button = new InlineKeyboardButton(String.join(" ", homeTeam, homeTeamScore, awayTeam, awayTeamScore));
            button.setCallbackData("/" + homeTeam + ":" + awayTeam);
            innerList.add(button);
            if (predictNum == matchPredictions.size()) {
                listOfKeyboardRows.add(innerList);
            }
            predictNum++;
        }
        keyBoard.setKeyboard(listOfKeyboardRows);
        return keyBoard;
    }

    private void deletePreviousMessage(Integer messageIdToDelete, String chatId) throws TelegramApiException {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setChatId(chatId);
        deleteMessage.setMessageId(messageIdToDelete);
        bot.execute(deleteMessage);
    }
}
