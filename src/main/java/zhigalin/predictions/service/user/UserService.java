package zhigalin.predictions.service.user;

import java.util.List;

import org.springframework.stereotype.Service;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.user.UserDao;


@Service
public class UserService {
    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public void save(User user) {
        userDao.save(user);
    }

    public List<User> findAll() {
        return userDao.findAll();
    }

    public User findById(int id) {
        return userDao.findById(id);
    }

    public User findByLogin(String login) {
        return userDao.findByLogin(login);
    }

    public User findByTelegramId(String telegramId) {
        return userDao.findByTelegramId(telegramId);
    }
}
