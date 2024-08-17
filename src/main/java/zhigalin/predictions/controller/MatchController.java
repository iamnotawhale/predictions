package zhigalin.predictions.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.model.event.Match;
import zhigalin.predictions.model.predict.Prediction;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/matches")
public class MatchController {
    private final MatchService matchService;
    private final WeekService weekService;
    private final TeamService teamService;
    private final StandingService standingService;

    @GetMapping("/team")

    public ModelAndView findByTeamId(@RequestParam(value = "id") int publicId) {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи " + teamService.findByPublicId(publicId).getName());
        model.addObject("matchList", matchService.findByPublicId(publicId));
        return model;
    }

    @GetMapping("/week/{id}")
    public ModelAndView findByWeekId(@PathVariable int id) {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи " + id + " тура");
        model.addObject("matchList", matchService.findAllByWeekId(id));
        return model;
    }

    @GetMapping("/week/current")
    public ModelAndView findByCurrentWeek() {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи " + weekService.findCurrentWeek().getId() + " тура");
        model.addObject("matchList", matchService.findAllByCurrentWeek());
        return model;
    }

    @GetMapping("/{id}")
    public Match findById(@PathVariable int id) {
        return matchService.findByPublicId(id);
    }

    @GetMapping("/byNames")
    public Match findByTeamNames(@RequestParam String home, @RequestParam String away) {
        return matchService.findByTeamNames(home, away);
    }

    @GetMapping("/byCodes")
    public Match findByTeamCodes(@RequestParam String home, @RequestParam String away) {
        return matchService.findByTeamCodes(home, away);
    }

    @GetMapping("/result/byNames")
    public List<Integer> getResultByTeamNames(@RequestParam String homeTeamName, @RequestParam String awayTeamName) {
        return matchService.getResultByTeamNames(homeTeamName, awayTeamName);
    }

    @GetMapping("/today")
    public ModelAndView findTodayMatches() {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи сегодня");
        model.addObject("matchList", matchService.findAllByTodayDate());
        return model;
    }

    @GetMapping("/upcoming")
    public ModelAndView findUpcomingMatches(@RequestParam Integer days) {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи в ближайшие дни - " + days);
        model.addObject("matchList", matchService.findAllByUpcomingDays(days));
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

    @ModelAttribute("currentWeek")
    public Integer getCurrentWeekId() {
        return weekService.findCurrentWeek().getId();
    }

    @ModelAttribute("todayDateTime")
    public LocalDateTime getTodayDateTime() {
        return LocalDateTime.now().minusMinutes(5L);
    }

    @ModelAttribute("newPredict")
    public Prediction newPrediction() {
        return Prediction.builder().build();
    }

    @ModelAttribute("places")
    public Map<Integer, Integer> places() {
        return standingService.getPlaces();
    }
}
