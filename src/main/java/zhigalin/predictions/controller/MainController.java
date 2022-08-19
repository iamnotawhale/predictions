package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.service.predict.PredictionService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping()
public class MainController {
    private final MatchService matchService;
    private final StandingService standingService;
    private final PredictionService predictionService;
    private final NewsService newsService;

    private final HeadToHeadService headToHeadService;

    @Autowired
    public MainController(MatchService matchService, StandingService standingService, PredictionService predictionService, NewsService newsService, HeadToHeadService headToHeadService) {
        this.matchService = matchService;
        this.standingService = standingService;
        this.predictionService = predictionService;
        this.newsService = newsService;
        this.headToHeadService = headToHeadService;
    }

    @GetMapping
    public ModelAndView getMainPage(Authentication authentication) {
        UserDto dto = getCurrentUser(authentication);
        List<List<HeadToHeadDto>> listOfHeadToHeads = new ArrayList<>();
        List<MatchDto> allByCurrentWeek = matchService.getAllByCurrentWeek(true);
        for (MatchDto matchDto : allByCurrentWeek) {
            List<HeadToHeadDto> allByMatch = headToHeadService.getAllByMatch(matchDto);
            listOfHeadToHeads.add(allByMatch);
        }

        ModelAndView model = new ModelAndView("main");
        model.addObject("map", predictionService.allUsersPoints());
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("currentUser", dto);
        model.addObject("matchList", allByCurrentWeek);
        model.addObject("h2h", listOfHeadToHeads);
        model.addObject("online", matchService.getOnline());
        model.addObject("standings", standingService.getAll());
        model.addObject("news", newsService.getAllLast());
        model.addObject("formatter", DateTimeFormatter.ofPattern("HH:mm"));

        return model;
    }

    public UserDto getCurrentUser(Authentication a) {
        User user = (User) a.getPrincipal();
        return UserDto.builder().id(user.getId()).login(user.getLogin()).build();
    }
}
