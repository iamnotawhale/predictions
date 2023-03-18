package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.football.TeamService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/matches")
public class MatchController {

    private final MatchService service;
    private final WeekService weekService;
    private final TeamService teamService;
    private final StandingService standingService;

    @GetMapping("/team")

    public ModelAndView findByTeamId(@RequestParam(value = "id") Long id) {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи " + teamService.findById(id).getName());
        model.addObject("matchList", service.findAllByTeamId(id));
        return model;
    }

    @GetMapping("/week/{id}")
    public ModelAndView findByWeekId(@PathVariable Long id) {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи " + id + " тура");
        model.addObject("matchList", service.findAllByWeekId(id));
        return model;
    }

    @GetMapping("/week/current")
    public ModelAndView findByCurrentWeek() {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи " + weekService.findCurrentWeek().getId() + " тура");
        model.addObject("matchList", service.findAllByCurrentWeek());
        return model;
    }

    @GetMapping("/{id}")
    public MatchDto findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/byNames")
    public MatchDto findByTeamNames(@RequestParam String home, @RequestParam String away) {
        return service.findByTeamNames(home, away);
    }

    @GetMapping("/byCodes")
    public MatchDto findByTeamCodes(@RequestParam String home, @RequestParam String away) {
        return service.findByTeamCodes(home, away);
    }

    @GetMapping("/result/byNames")
    public List<Integer> getResultByTeamNames(@RequestParam String homeTeamName, @RequestParam String awayTeamName) {
        return service.getResultByTeamNames(homeTeamName, awayTeamName);
    }

    @GetMapping("/today")
    public ModelAndView findTodayMatches() {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи сегодня");
        model.addObject("matchList", service.findAllByTodayDate());
        return model;
    }

    @GetMapping("/upcoming")
    public ModelAndView findUpcomingMatches(@RequestParam Integer days) {
        ModelAndView model = new ModelAndView("match");
        model.addObject("header", "Матчи в ближайшие дни - " + days);
        model.addObject("matchList", service.findAllByUpcomingDays(days));
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

    @ModelAttribute("currentWeek")
    public Long getCurrentWeekId() {
        return weekService.findCurrentWeek().getId();
    }

    @ModelAttribute("todayDateTime")
    public LocalDateTime getTodayDateTime() {
        return LocalDateTime.now().minusMinutes(5L);
    }

    @ModelAttribute("newPredict")
    public PredictionDto newPrediction() {
        return PredictionDto.builder().build();
    }

    @ModelAttribute("places")
    public Map<Long, Integer> places() {
        return standingService.getPlaces();
    }
}
