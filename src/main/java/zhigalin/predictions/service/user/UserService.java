package zhigalin.predictions.service.user;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.user.UserDao;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserService {
    private final UserDao repository;
    private final PasswordEncoder bCryptPasswordEncoder;

    public User save(User user) {
        User userFromDB = repository.findByLogin(user.getLogin());
        if (userFromDB != null) {
            return userFromDB;
        }
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        return repository.save(user);
    }

    public List<User> findAll() {
        return repository.findAll();
    }

    public User findById(int id) {
        return repository.findById(id).orElse(null);
    }

    public User findByLogin(String login) {
        return repository.findByLogin(login);
    }

    public User findByTelegramId(String telegramId) {
        return repository.findByTelegramId(telegramId);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
