package zhigalin.predictions.service.user;


import zhigalin.predictions.dto.user.UserDto;

import java.util.List;
import java.util.Map;

public interface UserService {
    UserDto saveUser(UserDto userDto);
    void delete(Long id);
    UserDto getByLogin(String login);
    List<UserDto> getAll();
    UserDto getById(Long id);
    List<UserDto> saveAll(List<UserDto> list);
    Integer getPointsByUserId(Long id);
    Map<UserDto, Integer> getAllPoints();
}
