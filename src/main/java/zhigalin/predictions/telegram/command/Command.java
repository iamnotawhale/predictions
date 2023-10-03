package zhigalin.predictions.telegram.command;

import com.rometools.rome.io.FeedException;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.IOException;
import java.text.ParseException;

public interface Command {

    void execute(Update update) throws FeedException, IOException, ParseException;
}
