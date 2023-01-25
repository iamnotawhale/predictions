package zhigalin.predictions.service.user;


import zhigalin.predictions.dto.user.UserDto;
import java.util.List;

public interface UserService {

    UserDto save(UserDto userDto);

    List<UserDto> saveAll(List<UserDto> list);

    UserDto findById(Long id);

    UserDto findByLogin(String login);

    List<UserDto> findAll();

    void delete(Long id);
}
