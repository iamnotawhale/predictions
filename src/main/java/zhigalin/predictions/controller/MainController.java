package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.service.predict.PredictionService;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@RestController
@RequestMapping
public class MainController {

    private final MatchService matchService;
    private final StandingService standingService;
    private final PredictionService predictionService;
    private final NewsService newsService;
    private final HeadToHeadService headToHeadService;
    private final WeekService weekService;

    @GetMapping()
    public ModelAndView getMainPage() {
        ModelAndView model = new ModelAndView("main");
        model.addObject("map", predictionService.allUsersPoints());
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("matchList", matchService.getAllByCurrentWeek());
        model.addObject("h2h", headToHeadService.getAllByCurrentWeek());
        model.addObject("online", matchService.getOnline());
        model.addObject("standings", standingService.getAll());
        model.addObject("news", newsService.getLastNews());
        return model;
    }

    @ModelAttribute("currentUser")
    public UserDto getCurrentUser() {
        UserDetailsImpl userDetailsImpl = (UserDetailsImpl) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return UserDto.builder()
                .id(userDetailsImpl.getId())
                .login(userDetailsImpl.getLogin())
                .build();
    }
}
