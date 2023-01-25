package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class UpcomingCommand implements Command {

    private final SendBotMessageService sendBotMessageService;
    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        Long tour = null;
        List<MatchDto> upcomingMatches = matchService.findAllByUpcomingDays(7);
        StringBuilder builder = new StringBuilder();
        if (!upcomingMatches.isEmpty()) {
            for (MatchDto dto : upcomingMatches) {
                builder.append("`");
                if (!dto.getWeek().getId().equals(tour)) {
                    builder.append(dto.getWeek().getId()).append(" тур").append("\n");
                    tour = dto.getWeek().getId();
                }
                builder.append(dto.getHomeTeam().getCode()).append(" ")
                        .append("- ").append(dto.getAwayTeam().getCode());
                if (Objects.equals(dto.getStatus(), "pst")) {
                    builder.append(" ⏰ ").append(dto.getStatus());
                } else {
                    builder.append(" ⏱ ").append(dto.getLocalDateTime().format(DateTimeFormatter.ofPattern("dd.MM HH:mm")));
                }

                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        } else {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Матчей в ближайшие 7 дней нет");
        }
    }
}
