package zhigalin.predictions.telegram.command;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Update;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.telegram.service.SendBotMessageService;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class TodayMatchesCommand implements Command {

    private final SendBotMessageService sendBotMessageService;

    private final MatchService matchService;

    @Override
    public void execute(Update update) {
        Long tour = null;
        List<MatchDto> list = matchService.findAllByTodayDate();
        StringBuilder builder = new StringBuilder();
        if (!list.isEmpty()) {
            for (MatchDto dto : list) {
                builder.append("`");
                if (!dto.getWeek().getId().equals(tour)) {
                    builder.append(dto.getWeek().getId()).append(" тур").append("\n");
                    tour = dto.getWeek().getId();
                }
                builder.append(dto.getHomeTeam().getCode()).append(" ");
                if (!Objects.equals(dto.getStatus(), "ns") && !Objects.equals(dto.getStatus(), "pst")) {
                    builder.append(dto.getHomeTeamScore()).append(" - ")
                            .append(dto.getAwayTeamScore()).append(" ")
                            .append(dto.getAwayTeam().getCode()).append(" ")
                            .append(dto.getStatus()).append(" ");
                } else if (Objects.equals(dto.getStatus(), "pst")) {
                    builder.append("- ").append(dto.getAwayTeam().getCode())
                            .append(" ⏰ ").append(dto.getStatus());
                } else {
                    builder.append("- ").append(dto.getAwayTeam().getCode())
                            .append(" ⏱ ").append(dto.getMatchTime());
                }
                builder.append("`").append("\n");
            }
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), builder.toString());
        } else {
            sendBotMessageService.sendMessage(update.getMessage().getChatId().toString(), "Сегодня матчей нет");
        }

    }
}
