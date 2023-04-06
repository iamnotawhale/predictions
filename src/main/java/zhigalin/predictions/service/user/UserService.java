package zhigalin.predictions.service.user;


import zhigalin.predictions.dto.user.UserDto;
import java.util.List;

public interface UserService {
    UserDto save(UserDto dto);
    UserDto findById(Long id);
    UserDto findByLogin(String login);
    List<UserDto> findAll();
    void delete(Long id);
}
