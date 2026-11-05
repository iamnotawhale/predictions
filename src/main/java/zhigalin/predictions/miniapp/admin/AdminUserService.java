package zhigalin.predictions.miniapp.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import zhigalin.predictions.miniapp.MiniAppException;
import zhigalin.predictions.miniapp.MiniAppService;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminUserDetailResponse;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminUserItem;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.AdminUserPredictionRow;
import zhigalin.predictions.miniapp.dto.MiniAppDtos.MatchItem;
import zhigalin.predictions.model.event.CupMatch;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.repository.event.CupMatchDao;
import zhigalin.predictions.repository.predict.PredictionDao;
import zhigalin.predictions.service.user.UserService;

@Service
public class AdminUserService {

    private final AdminGuard adminGuard;
    private final UserService userService;
    private final PredictionDao predictionDao;
    private final CupMatchDao cupMatchDao;
    private final MiniAppService miniAppService;

    public AdminUserService(
            AdminGuard adminGuard,
            UserService userService,
            PredictionDao predictionDao,
            CupMatchDao cupMatchDao,
            MiniAppService miniAppService
    ) {
        this.adminGuard = adminGuard;
        this.userService = userService;
        this.predictionDao = predictionDao;
        this.cupMatchDao = cupMatchDao;
        this.miniAppService = miniAppService;
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
            MatchItem card = miniAppService.adminMatchCard(m);
            predictions.add(fromMatchCard("epl", m.getPublicId(), null, m.getStatus(), p, card));
        }
        for (Prediction p : predictionDao.findAllCupByUserId(userId)) {
            CupMatch c = cupMatchDao.findByPublicId(p.getCupMatchId());
            MatchItem card = c != null ? miniAppService.adminCupCard(c) : null;
            predictions.add(fromMatchCard(
                    "cup",
                    null,
                    p.getCupMatchId(),
                    c != null ? c.getStatus() : null,
                    p,
                    card
            ));
        }
        return new AdminUserDetailResponse(toItem(u), predictions);
    }

    private static AdminUserPredictionRow fromMatchCard(
            String kind,
            Integer matchPublicId,
            Integer cupMatchId,
            String matchStatus,
            Prediction p,
            MatchItem card
    ) {
        String label;
        String homeCode = "";
        String awayCode = "";
        String homeName = "";
        String awayName = "";
        String homeLogo = "";
        String awayLogo = "";
        if (card != null) {
            label = card.homeCode() + " – " + card.awayCode();
            homeCode = card.homeCode() != null ? card.homeCode() : "";
            awayCode = card.awayCode() != null ? card.awayCode() : "";
            homeName = card.homeName() != null ? card.homeName() : "";
            awayName = card.awayName() != null ? card.awayName() : "";
            homeLogo = card.homeLogo() != null ? card.homeLogo() : "";
            awayLogo = card.awayLogo() != null ? card.awayLogo() : "";
        } else {
            label = cupMatchId != null ? "cup #" + cupMatchId : "?";
        }
        return new AdminUserPredictionRow(
                kind,
                matchPublicId,
                cupMatchId,
                label,
                matchStatus,
                p.getHomeTeamScore(),
                p.getAwayTeamScore(),
                p.getPoints(),
                homeCode,
                awayCode,
                homeName,
                awayName,
                homeLogo,
                awayLogo
        );
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
}
