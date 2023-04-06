package zhigalin.predictions.dto.user;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.Role;

import java.util.List;
import java.util.Set;

@Value
@Builder
public class UserDto {
    Long id;
    String login;
    @NonFinal
    @Setter
    @EqualsAndHashCode.Exclude
    String password;
    @EqualsAndHashCode.Exclude
    Set<Role> roles;
    @EqualsAndHashCode.Exclude
    List<Prediction> predictions;
}
