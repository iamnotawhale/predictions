package zhigalin.predictions.service._impl.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.user.UserRepository;
import zhigalin.predictions.service.user.UserService;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper mapper;
    private final PasswordEncoder bCryptPasswordEncoder;

    @Override
    public UserDto saveUser(UserDto userDto) {
        User userFromDB = userRepository.findByLogin(userDto.getLogin());
        if (userFromDB != null) {
            return mapper.toDto(userFromDB);
        }
        userDto.setPassword(bCryptPasswordEncoder.encode(userDto.getPassword()));
        return mapper.toDto(userRepository.save(mapper.toEntity(userDto)));
    }

    @Override
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public UserDto getByLogin(String login) {
        User user = userRepository.findByLogin(login);
        if (user != null) {
            return mapper.toDto(user);
        }
        return null;
    }

    @Override
    public List<UserDto> getAll() {
        return StreamSupport.stream(userRepository.findAll().spliterator(), false).map(mapper::toDto).toList();
    }

    @Override
    public UserDto getById(Long id) {
        return mapper.toDto(userRepository.findById(id).orElse(null));
    }

    @Override
    public List<UserDto> saveAll(List<UserDto> list) {
        List<User> listUser = list.stream().map(mapper::toEntity).collect(Collectors.toList());
        return StreamSupport.stream(userRepository.saveAll(listUser).spliterator(), false).map(mapper::toDto).toList();
    }
}
