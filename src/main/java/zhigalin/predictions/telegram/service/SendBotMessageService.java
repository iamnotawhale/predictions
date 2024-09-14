package zhigalin.predictions.telegram.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.NotificationService;
import zhigalin.predictions.telegram.model.EPLInfoBot;
import zhigalin.predictions.util.DaoUtil;

@Service
public class SendBotMessageService {

    private final EPLInfoBot bot;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final NotificationService notificationService;

    public SendBotMessageService(EPLInfoBot bot, NotificationService notificationService) {
        this.bot = bot;
        this.notificationService = notificationService;
    }

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
    public void sendMessageDeletingKeyboard(String chatId, String message) {
        deletePreviousMessage(chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendAlertDeletingKeyboard(String chatId, String callbackId, String message) {
        deletePreviousMessage(chatId);

        AnswerCallbackQuery query = new AnswerCallbackQuery();
        query.setCallbackQueryId(callbackId);
        query.setText(message);
        query.setShowAlert(true);
        query.setCacheTime(10);

        bot.execute(query);
    }

    @SneakyThrows
    public void sendPredictKeyBoard(String chatId, String message, String homeTeam, String awayTeam, Prediction prediction) {
        deletePreviousMessage(chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createPredictKeyBoard(homeTeam, awayTeam, prediction));

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendNotificationPredictKeyBoard(String chatId, String message, String homeTeam, String awayTeam) {
        deletePreviousMessage(chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createNotificationPredictKeyBoard(homeTeam, awayTeam));

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendTourKeyBoard(String chatId, List<Integer> weeksIds, String message, String prefix) {
        deletePreviousMessage(chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createTourKeyBoard(weeksIds, prefix));

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendWeeklyPredictsByUserKeyBoard(String chatId, String message, List<MatchPrediction> matchPredictions) {
        deletePreviousMessage(chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createPredictTourUsersKeyBoard(matchPredictions));

        bot.execute(sendMessage);
    }


    @SneakyThrows
    public void sendMessageWithMatchesKeyboard(List<Match> matches, List<Integer> predictableMatches, String chatId, String message) {
        deletePreviousMessage(chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createMatchesKeyBoard(matches, predictableMatches));

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendMainMenuMessage(String chatId, String message) {
        deletePreviousMessage(chatId);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableHtml(true);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(createMenuKeyBoard());

        bot.execute(sendMessage);
    }

    @SneakyThrows
    public void sendMessageNotificationPicture(String chatId, String message, Match match, int homePredict, int awayPredict) {
        deletePreviousMessage(chatId);

        InputFile inputFile = new InputFile();
        inputFile.setMedia(new File(Objects.requireNonNull(
                notificationService.createImage(
                        match.getPublicId(),
                        match.getHomeTeamId(),
                        match.getAwayTeamId(),
                        homePredict + ":" + awayPredict,
                        "yourPredict",
                        null
                )
        )));

        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(inputFile);
//        sendPhoto.setCaption(message);

        String homeTeam = DaoUtil.TEAMS.get(match.getHomeTeamId()).getCode();
        String awayTeam = DaoUtil.TEAMS.get(match.getAwayTeamId()).getCode();
        sendPhoto.setReplyMarkup(
                InlineKeyboardMarkup.builder()
                        .keyboard(
                                Collections.singleton(List.of(
                                        InlineKeyboardButton.builder()
                                                .text("Изменить")
                                                .callbackData("/" + homeTeam + ":" + awayTeam + "_")
                                                .build()
                                ))
                        )
                        .build()
        );

        bot.execute(sendPhoto);
    }

    private static InlineKeyboardMarkup createMenuKeyBoard() {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton weeks = new InlineKeyboardButton("Сделать прогноз");
        weeks.setCallbackData("/tours");
        row1.add(weeks);


        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton predicts = new InlineKeyboardButton("Мои прогнозы");
        predicts.setCallbackData("/predicts");
        row2.add(predicts);

        listOfKeyboard.add(row1);
        listOfKeyboard.add(row2);

        keyBoard.setKeyboard(listOfKeyboard);
        return keyBoard;

    }

    private static InlineKeyboardMarkup createPredictKeyBoard(String homeTeam, String awayTeam, Prediction prediction) {
        Integer predictHomeScore = null;
        Integer predictAwayScore = null;
        if (prediction != null) {
            predictHomeScore = prediction.getHomeTeamScore();
            predictAwayScore = prediction.getAwayTeamScore();
        }

        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboard = new ArrayList<>();
        for (Integer awayScore = 0; awayScore < 6; awayScore++) {
            List<InlineKeyboardButton> innerList = new ArrayList<>();
            for (Integer homeScore = 0; homeScore < 6; homeScore++) {
                String buttonName = homeScore + ":" + awayScore;
                if (homeScore.equals(predictHomeScore) && awayScore.equals(predictAwayScore)) {
                    buttonName = String.join("̲", buttonName.split("", -1));
                }
                InlineKeyboardButton button = new InlineKeyboardButton(buttonName);
                button.setCallbackData("/pred " + homeTeam + " " + homeScore + " " + awayTeam + " " + awayScore);
                innerList.add(button);
            }
            listOfKeyboard.add(innerList);
        }
        if (prediction != null) {
            InlineKeyboardButton deletePredictButton = new InlineKeyboardButton("Удалить");
            deletePredictButton.setCallbackData("/delete " + homeTeam + " " + awayTeam);
            listOfKeyboard.add(List.of(deletePredictButton));
        }
        InlineKeyboardButton backButton = new InlineKeyboardButton("Отмена");
        backButton.setCallbackData("/cancel");
        listOfKeyboard.add(List.of(backButton));
        keyBoard.setKeyboard(listOfKeyboard);
        return keyBoard;
    }

    private static InlineKeyboardMarkup createNotificationPredictKeyBoard(String homeTeam, String awayTeam) {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboard = new ArrayList<>();
        for (int awayScore = 0; awayScore < 6; awayScore++) {
            List<InlineKeyboardButton> innerList = new ArrayList<>();
            for (int homeScore = 0; homeScore < 6; homeScore++) {
                InlineKeyboardButton button = new InlineKeyboardButton(homeScore + ":" + awayScore);
                button.setCallbackData("/notpred " + homeTeam + " " + homeScore + " " + awayTeam + " " + awayScore);
                innerList.add(button);
            }
            listOfKeyboard.add(innerList);
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton("Отмена");
        backButton.setCallbackData("/cancel");
        listOfKeyboard.add(List.of(backButton));
        keyBoard.setKeyboard(listOfKeyboard);
        return keyBoard;
    }

    private static InlineKeyboardMarkup createTourKeyBoard(List<Integer> weeksIds, String prefix) {
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
                String buttonName = String.valueOf(tour);
                if (weeksIds.contains(tour)) {
                    buttonName += " *";
                }
                InlineKeyboardButton button = new InlineKeyboardButton(buttonName);
                button.setCallbackData("/" + prefix + tour);
                innerList.add(button);
            }
            listOfKeyboardRows.add(innerList);
        }
        InlineKeyboardButton backButton = new InlineKeyboardButton("« Назад");
        backButton.setCallbackData("/menu");
        listOfKeyboardRows.add(List.of(backButton));
        keyBoard.setKeyboard(listOfKeyboardRows);
        return keyBoard;
    }

    private static InlineKeyboardMarkup createMatchesKeyBoard(List<Match> matches, List<Integer> predictableMatches) {
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

            String buttonName = String.join("-", homeTeam, awayTeam);
            if (predictableMatches.contains(match.getPublicId())) {
                buttonName += " *";
            }

            InlineKeyboardButton button = new InlineKeyboardButton(buttonName);
            button.setCallbackData("/" + homeTeam + ":" + awayTeam);
            innerList.add(button);
            if (matchNum == matches.size()) {
                listOfKeyboardRows.add(innerList);
            }
            matchNum++;
        }
        InlineKeyboardButton backButton = new InlineKeyboardButton("« Назад");
        backButton.setCallbackData("/tours");
        listOfKeyboardRows.add(List.of(backButton));
        keyBoard.setKeyboard(listOfKeyboardRows);
        return keyBoard;
    }

    private static InlineKeyboardMarkup createTodayMatchesKeyBoard(List<Match> matches, List<Integer> predictableMatches) {
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

            String buttonName = String.join("-", homeTeam, awayTeam);
            if (predictableMatches.contains(match.getPublicId())) {
                buttonName += " *";
            }

            InlineKeyboardButton button = new InlineKeyboardButton(buttonName);
            button.setCallbackData("/" + homeTeam + ":" + awayTeam);
            innerList.add(button);
            if (matchNum == matches.size()) {
                listOfKeyboardRows.add(innerList);
            }
            matchNum++;
        }
        InlineKeyboardButton backButton = new InlineKeyboardButton("« Назад");
        backButton.setCallbackData("/today");
        listOfKeyboardRows.add(List.of(backButton));
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

            InlineKeyboardButton button = new InlineKeyboardButton(
                    String.join(" ",
                            homeTeam,
                            homeTeamScore,
                            awayTeam,
                            awayTeamScore
                    )
            );
            button.setCallbackData("/" + homeTeam + ":" + awayTeam);
            innerList.add(button);
            if (predictNum == matchPredictions.size()) {
                listOfKeyboardRows.add(innerList);
            }
            predictNum++;
        }
        InlineKeyboardButton backButton = new InlineKeyboardButton("« Назад");
        backButton.setCallbackData("/predicts");
        listOfKeyboardRows.add(List.of(backButton));
        keyBoard.setKeyboard(listOfKeyboardRows);
        return keyBoard;
    }

    public void deletePreviousMessage(String chatId) {
        try {
            Integer messageId = bot.getMessageToDelete().getOrDefault(Long.parseLong(chatId), null);
            if (messageId == null) {
                return;
            }

            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId);
            deleteMessage.setMessageId(messageId);

            bot.execute(deleteMessage);
        } catch (Exception e) {
            serverLogger.error("Error on delete message: ", e);
        }
    }
}
