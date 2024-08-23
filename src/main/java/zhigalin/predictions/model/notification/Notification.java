package zhigalin.predictions.model.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.user.User;

@EqualsAndHashCode
@AllArgsConstructor
@Builder
@Getter
public class Notification {
    User user;
    Match match;

    @Override
    public String toString() {
        return "notification#" + user.getId() + match.getPublicId();
    }
}
