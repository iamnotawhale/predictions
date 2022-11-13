package zhigalin.predictions.telegram.service;

public interface SendBotMessageService {

    void sendMessage(String chatId, String message);
}
