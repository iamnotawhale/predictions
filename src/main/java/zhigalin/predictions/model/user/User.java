package zhigalin.predictions.model.user;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class User {

    private int id;
    private String login;
    private String password;
    private String telegramId;
    private String role;
}
