package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping()
public class MainController {

    private final MatchService matchService;
    private final StandingService standingService;
    private final UserService userService;
    private final PredictionService predictionService;
    private final NewsService newsService;

    @Autowired
    public MainController(MatchService matchService, StandingService standingService, UserService userService, PredictionService predictionService, NewsService newsService) {
        this.matchService = matchService;
        this.standingService = standingService;
        this.userService = userService;
        this.predictionService = predictionService;
        this.newsService = newsService;
    }

    @GetMapping
    public ModelAndView getMainPage(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setLogin(user.getLogin());

        Map<UserDto, Integer> userPointsMap = new HashMap<>();
        List<UserDto> userList = userService.getAll();
        for (UserDto userDto : userList) {
            userPointsMap.put(userDto, predictionService.getUsersPointsByUserId(userDto.getId()));
        }
        Map<UserDto, Integer> sortedMap = userPointsMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        ModelAndView model = new ModelAndView("main");
        model.addObject("map", sortedMap);
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("currentUser", dto);
        model.addObject("matchList", matchService.getAllByCurrentWeek(true));
        model.addObject("online", matchService.getOnline());
        model.addObject("standings", standingService.getAll());
        model.addObject("news", newsService.getAllLast());
        model.addObject("formatter", DateTimeFormatter.ofPattern("HH:mm"));

        return model;
    }
}
