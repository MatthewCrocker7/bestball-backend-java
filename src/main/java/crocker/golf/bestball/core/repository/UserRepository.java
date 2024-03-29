package crocker.golf.bestball.core.repository;

import crocker.golf.bestball.core.dao.UserDao;
import crocker.golf.bestball.domain.user.UserCredentials;
import crocker.golf.bestball.domain.user.UserInfo;

import java.util.UUID;

public class UserRepository {

    private final UserDao userDao;

    public UserRepository(UserDao userDao) {
        this.userDao = userDao;
    }

    public void save(UserCredentials userCredentials) {
        userDao.save(userCredentials);
    }

    public UserCredentials findByUsername(String username) {
        return userDao.findByUserName(username);
    }

    public UserCredentials findByEmail(String email) {
        return userDao.findByEmail(email.toLowerCase());
    }

    public UserCredentials getUserByUserId(UUID userId) {
        return userDao.getUserByUserId(userId);
    }
}
