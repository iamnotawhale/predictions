package zhigalin.predictions.miniapp.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminUserDetailResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminUserItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminUserPredictionRow;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.service.user.UserService;
import zhigalin.predictions.util.DaoUtil;

@Service
public class AdminUserService {

    private final AdminGuard adminGuard;
    private final UserService userService;
    private final PredictionDao predictionDao;
    private final CupMatchDao cupMatchDao;

    public AdminUserService(
            AdminGuard adminGuard,
            UserService userService,
            PredictionDao predictionDao,
            CupMatchDao cupMatchDao
    ) {
        this.adminGuard = adminGuard;
        this.userService = userService;
        this.predictionDao = predictionDao;
        this.cupMatchDao = cupMatchDao;
    }

    public List<AdminUserItem> listUsers(String telegramId) {
        adminGuard.requireAdmin(telegramId);
        return userService.findAll().stream()
                .sorted(Comparator.comparing(User::getLogin, String.CASE_INSENSITIVE_ORDER))
                .map(this::toItem)
                .toList();
    }

    public AdminUserDetailResponse getUser(String telegramId, int userId) {
        adminGuard.requireAdmin(telegramId);
        User u = userService.findAll().stream()
                .filter(x -> x.getId() == userId)
                .findFirst()
                .orElseThrow(() -> new MiniAppException(404, "Пользователь не найден"));
        List<AdminUserPredictionRow> predictions = new ArrayList<>();
        for (PredictionDao.MatchPrediction mp : predictionDao.findAllByUserId(userId)) {
            Match m = mp.match();
            Prediction p = mp.prediction();
            predictions.add(new AdminUserPredictionRow(
                    "epl",
                    m.getPublicId(),
                    null,
                    teamCode(m.getHomeTeamId()) + " – " + teamCode(m.getAwayTeamId()),
                    m.getStatus(),
                    p.getHomeTeamScore(),
                    p.getAwayTeamScore(),
                    p.getPoints()
            ));
        }
        for (Prediction p : predictionDao.findAllCupByUserId(userId)) {
            CupMatch c = cupMatchDao.findByPublicId(p.getCupMatchId());
            predictions.add(new AdminUserPredictionRow(
                    "cup",
                    null,
                    p.getCupMatchId(),
                    c != null ? cupLabel(c) : "cup #" + p.getCupMatchId(),
                    c != null ? c.getStatus() : null,
                    p.getHomeTeamScore(),
                    p.getAwayTeamScore(),
                    p.getPoints()
            ));
        }
        return new AdminUserDetailResponse(toItem(u), predictions);
    }

    private AdminUserItem toItem(User u) {
        return new AdminUserItem(
                u.getId(),
                u.getLogin(),
                u.getTelegramId(),
                u.getRole(),
                u.isBettingRecommenderEnabled(),
                predictionDao.findAllByUserId(u.getId()).size()
                        + predictionDao.findAllCupByUserId(u.getId()).size()
        );
    }

    private static String teamCode(int teamId) {
        var team = DaoUtil.TEAMS.get(teamId);
        return team != null && team.getCode() != null ? team.getCode() : "?";
    }

    private static String cupLabel(CupMatch c) {
        String h = c.getHomeEspnCode() != null && !c.getHomeEspnCode().isBlank()
                ? c.getHomeEspnCode()
                : c.getHomeName();
        String a = c.getAwayEspnCode() != null && !c.getAwayEspnCode().isBlank()
                ? c.getAwayEspnCode()
                : c.getAwayName();
        return (h != null ? h : "?") + " – " + (a != null ? a : "?");
    }
}
