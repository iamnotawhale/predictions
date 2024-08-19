package zhigalin.predictions.controller;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;

import com.rometools.rome.io.FeedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.DataInitService;
import zhigalin.predictions.util.DaoUtil;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping
public class MainController {
    private final MatchService matchService;
    private final HeadToHeadService headToHeadService;
    private final WeekService weekService;
    private final DataInitService dataInitService;
    private final UserService userService;
    private final PredictionService predictionService;

    @GetMapping()
    public ModelAndView getMainPage() throws FeedException, IOException, ParseException {
        ModelAndView model = new ModelAndView("main");
        model.addObject("map", predictionService.getAllPointsByUsers());
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("currentWeek", weekService.findCurrentWeek().getId());
        model.addObject("matchList", matchService.findAllByCurrentWeek());
        model.addObject("h2h", headToHeadService.findAllByCurrentWeekNew());
        model.addObject("online", matchService.findOnlineTeamsIds());
        model.addObject("onlineMatches", matchService.findOnlineMatches());
        model.addObject("standings", matchService.getStandings());
        model.addObject("news", dataInitService.newsInit());
        model.addObject("teams", DaoUtil.TEAMS);
        return model;
    }

    @ModelAttribute("currentUser")
    public User getCurrentUser() {
        UserDetails userDetails = (UserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return userService.findByLogin(userDetails.getUsername());
    }
}
