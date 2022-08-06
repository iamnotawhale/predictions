package zhigalin.predictions.service_impl.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import zhigalin.predictions.converter.user.UserMapper;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.user.UserRepository;
import zhigalin.predictions.service.user.UserService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    private final UserRepository userRepository;
    private final UserMapper mapper;

    private final PasswordEncoder bCryptPasswordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, UserMapper mapper, PasswordEncoder bCryptPasswordEncoder) {
        this.userRepository = userRepository;
        this.mapper = mapper;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    }

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
        return mapper.toDto(userRepository.findById(id).get());
    }

    @Override
    public List<UserDto> saveAll(List<UserDto> list) {
        List<User> listUser = list.stream().map(mapper::toEntity).collect(Collectors.toList());
        return StreamSupport.stream(userRepository.saveAll(listUser).spliterator(), false).map(mapper::toDto).toList();
    }

    @Override
    public Integer getPointsByUserId(Long id) {
        return userRepository.getPointsByUserId(id);
    }

    @Override
    public Map<UserDto, Integer> getAllPoints() {
        HashMap<UserDto, Integer> map = new HashMap<>();
        List<UserDto> allUsers = getAll();
        for (UserDto dto : allUsers) {
            map.put(dto, getPointsByUserId(dto.getId()));
        }
        return map.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        User user = userRepository.findByLogin(login);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }

        return user;
    }
}
