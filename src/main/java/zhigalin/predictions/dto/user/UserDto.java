package zhigalin.predictions.dto.user;

import lombok.*;
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
    String password;

    Set<Role> roles;

    List<Prediction> predictions;
}
