package zhigalin.predictions.telegram.command;

import java.io.IOException;
import java.text.ParseException;

import com.rometools.rome.io.FeedException;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;

public interface Command {

    void execute(Update update) throws FeedException, IOException, ParseException;

    default void executeCallback(CallbackQuery callbackQuery) {}
}
