package zhigalin.predictions.model.notification;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import zhigalin.predictions.model.event.Lineup;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

@EqualsAndHashCode
@AllArgsConstructor
@Builder
@Getter
public class Notification {
    User user;
    Match match;
    Map<Integer, List<Lineup>> lineups;

    @Override
    public String toString() {
        return "notification#" + user.getId() + match.getPublicId();
    }
}
