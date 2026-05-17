package zhigalin.predictions.telegram.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.football.Team;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.repository.predict.PredictionDao.MatchPrediction;
import zhigalin.predictions.service.notification.ImageRenderer;
import zhigalin.predictions.telegram.model.EPLInfoBot;
import zhigalin.predictions.util.DaoUtil;

import static zhigalin.predictions.service.notification.NotificationImageMode.YOUR_PREDICT;

public class SendBotMessageService {

    private final EPLInfoBot bot;
    private final Logger serverLogger = LoggerFactory.getLogger("server");
    private final ImageRenderer imageRenderer;
    private final String webAppUrl;

    public SendBotMessageService(EPLInfoBot bot, ImageRenderer imageRenderer, String webAppUrl) {
        this.bot = bot;
        this.imageRenderer = imageRenderer;
        this.webAppUrl = webAppUrl == null ? "" : webAppUrl;
    }

    @SneakyThrows
    public void sendMessage(String chatId, String message) {
        bot.execute(createMessage(chatId, message, null));
    }

    @SneakyThrows
    public void sendMessageDeletingKeyboard(String chatId, String message) {
        deletePreviousMessage(chatId);
        bot.execute(createMessage(chatId, message, null));
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
        bot.execute(createMessage(chatId, message, createPredictKeyBoard(homeTeam, awayTeam, prediction)));
    }

    @SneakyThrows
    public void sendNotificationPredictKeyBoard(String chatId, String message, String homeTeam, String awayTeam) {
        deletePreviousMessage(chatId);
        bot.execute(createMessage(chatId, message, createNotificationPredictKeyBoard(homeTeam, awayTeam)));
    }

    @SneakyThrows
    public void sendTourKeyBoard(String chatId, List<Integer> weeksIds, String message, String prefix) {
        deletePreviousMessage(chatId);
        bot.execute(createMessage(chatId, message, createTourKeyBoard(weeksIds, prefix)));
    }

    @SneakyThrows
    public void sendWeeklyPredictsByUserKeyBoard(String chatId, String message, List<MatchPrediction> matchPredictions) {
        deletePreviousMessage(chatId);
        bot.execute(createMessage(chatId, message, createPredictTourUsersKeyBoard(matchPredictions)));
    }


    @SneakyThrows
    public void sendMessageWithMatchesKeyboard(List<Match> matches, List<Integer> predictableMatches, String chatId, String message) {
        deletePreviousMessage(chatId);
        bot.execute(createMessage(chatId, message, createMatchesKeyBoard(matches, predictableMatches)));
    }

    @SneakyThrows
    public void sendMainMenuMessage(String chatId, String message) {
        deletePreviousMessage(chatId);
        bot.execute(createMessage(chatId, message, createMenuKeyBoard()));
    }

    @SneakyThrows
    public void sendMessageNotificationPicture(String chatId, String message, Match match, int homePredict, int awayPredict) {
        deletePreviousMessage(chatId);

        String imagePath = imageRenderer.createImage(
            match.getPublicId(),
            match.getHomeTeamId(),
            match.getAwayTeamId(),
            homePredict + ":" + awayPredict,
            YOUR_PREDICT,
            null
        );
        if (imagePath == null) {
            sendMessage(chatId, message);
            return;
        }

        InputFile inputFile = new InputFile();
        inputFile.setMedia(new File(imagePath));

        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(inputFile);
        sendPhoto.setCaption(message);

        Team home = DaoUtil.team(match.getHomeTeamId());
        Team away = DaoUtil.team(match.getAwayTeamId());
        if (home == null || away == null) {
            bot.execute(sendPhoto);
            return;
        }
        String homeTeam = home.getCode();
        String awayTeam = away.getCode();
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

    private static SendMessage createMessage(String chatId, String message, InlineKeyboardMarkup keyboard) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.enableMarkdown(true);
        sendMessage.setText(message);
        if (keyboard != null) {
            sendMessage.setReplyMarkup(keyboard);
        }
        return sendMessage;
    }

    private InlineKeyboardMarkup createMenuKeyBoard() {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboard = new ArrayList<>();

        if (!webAppUrl.isBlank()) {
            InlineKeyboardButton appButton = InlineKeyboardButton.builder()
                    .text("📱 Открыть приложение")
                    .webApp(WebAppInfo.builder().url(webAppUrl).build())
                    .build();
            listOfKeyboard.add(List.of(appButton));
        }

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
        List<List<InlineKeyboardButton>> listOfKeyboard = createScoreGrid(homeTeam, awayTeam, "/pred ", true, predictHomeScore, predictAwayScore);
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
        List<List<InlineKeyboardButton>> listOfKeyboard = createScoreGrid(homeTeam, awayTeam, "/notpred ", false, null, null);

        InlineKeyboardButton backButton = new InlineKeyboardButton("Отмена");
        backButton.setCallbackData("/cancel");
        listOfKeyboard.add(List.of(backButton));
        keyBoard.setKeyboard(listOfKeyboard);
        return keyBoard;
    }

    private static List<List<InlineKeyboardButton>> createScoreGrid(String homeTeam,
                                                                    String awayTeam,
                                                                    String callbackPrefix,
                                                                    boolean highlightSelected,
                                                                    Integer selectedHomeScore,
                                                                    Integer selectedAwayScore) {
        List<List<InlineKeyboardButton>> listOfKeyboard = new ArrayList<>();
        for (int awayScore = 0; awayScore < 6; awayScore++) {
            List<InlineKeyboardButton> innerList = new ArrayList<>();
            for (int homeScore = 0; homeScore < 6; homeScore++) {
                String buttonName = homeScore + ":" + awayScore;
                if (highlightSelected
                    && Integer.valueOf(homeScore).equals(selectedHomeScore)
                    && Integer.valueOf(awayScore).equals(selectedAwayScore)) {
                    buttonName = String.join("̲", buttonName.split("", -1));
                }
                InlineKeyboardButton button = new InlineKeyboardButton(buttonName);
                button.setCallbackData(callbackPrefix + homeTeam + " " + homeScore + " " + awayTeam + " " + awayScore);
                innerList.add(button);
            }
            listOfKeyboard.add(innerList);
        }
        return listOfKeyboard;
    }

    private static InlineKeyboardMarkup createTourKeyBoard(List<Integer> weeksIds, String prefix) {
        InlineKeyboardMarkup keyBoard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> listOfKeyboardRows = new ArrayList<>();
        Set<Integer> markedWeeks = new HashSet<>(weeksIds);
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
                if (markedWeeks.contains(tour)) {
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
        Set<Integer> predictableMatchIds = new HashSet<>(predictableMatches);
        int matchNum = 1;
        List<InlineKeyboardButton> innerList = new ArrayList<>();
        for (Match match : matches) {
            if (matchNum > 1 && matchNum % 2 == 1) {
                listOfKeyboardRows.add(innerList);
                innerList = new ArrayList<>();
            }
            Team home = DaoUtil.team(match.getHomeTeamId());
            Team away = DaoUtil.team(match.getAwayTeamId());
            if (home == null || away == null) {
                continue;
            }
            String homeTeam = home.getCode();
            String awayTeam = away.getCode();

            String buttonName = String.join("-", homeTeam, awayTeam);
            if (predictableMatchIds.contains(match.getPublicId())) {
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
            Integer messageId = bot.getMessageToDelete().get(Long.parseLong(chatId));
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
