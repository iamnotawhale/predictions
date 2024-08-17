package zhigalin.predictions.controller;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;

import com.rometools.rome.io.FeedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.predict.PointsService;

@RequiredArgsConstructor
@RestController
@RequestMapping
public class MainController {
    private final MatchService matchService;
    private final StandingService standingService;
    private final HeadToHeadService headToHeadService;
    private final WeekService weekService;
    private final PointsService pointsService;
    private final DataInitService dataInitService;

    @GetMapping()
    public ModelAndView getMainPage() throws FeedException, IOException, ParseException {
        ModelAndView model = new ModelAndView("main");
        model.addObject("map", pointsService.getAll());
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("currentWeek", weekService.findCurrentWeek().getId());
        model.addObject("matchList", matchService.findAllByCurrentWeek());
        model.addObject("h2h", headToHeadService.findAllByCurrentWeek());
        model.addObject("online", matchService.findOnline());
        model.addObject("standings", standingService.findAll());
        model.addObject("news", dataInitService.newsInit());
        return model;
    }

    @ModelAttribute("currentUser")
    public User getCurrentUser() {
        UserDetailsImpl userDetailsImpl = (UserDetailsImpl) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return User.builder()
                .id(userDetailsImpl.getId())
                .login(userDetailsImpl.getLogin())
                .build();
    }
}
