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

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final UserMapper mapper;
    private final PasswordEncoder bCryptPasswordEncoder;

    @Override
    public UserDto save(UserDto dto) {
        User userFromDB = repository.findByLogin(dto.getLogin());
        if (userFromDB != null) {
            return mapper.toDto(userFromDB);
        }
        dto.setPassword(bCryptPasswordEncoder.encode(dto.getPassword()));
        return mapper.toDto(repository.save(mapper.toEntity(dto)));
    }

    @Override
    public List<UserDto> saveAll(List<UserDto> list) {
        List<User> listUser = list.stream().map(mapper::toEntity).collect(Collectors.toList());
        return repository.saveAll(listUser).stream().map(mapper::toDto).toList();
    }

    @Override
    public List<UserDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Override
    public UserDto findById(Long id) {
        return mapper.toDto(repository.findById(id).orElse(null));
    }

    @Override
    public UserDto findByLogin(String login) {
        User user = repository.findByLogin(login);
        if (user != null) {
            return mapper.toDto(user);
        }
        return null;
    }

    @Override
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
