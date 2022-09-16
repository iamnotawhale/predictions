package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/match")
public class MatchController {
    private final MatchService service;
    private final WeekService weekService;
    private final TeamService teamService;

    @Autowired
    public MatchController(MatchService service, WeekService weekService, TeamService teamService) {
        this.service = service;
        this.weekService = weekService;
        this.teamService = teamService;
    }

    @GetMapping("/team")
    public ModelAndView findByTeamId(@RequestParam(value = "id") Long id, Authentication authentication) {
        UserDto dto = getCurrentUser(authentication);
        ModelAndView model = new ModelAndView("match");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("currentUser", dto);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("header", "Матчи " + teamService.getById(id).getTeamName());
        model.addObject("matchList", service.getAllByTeamId(id));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping("/week/{id}")
    public ModelAndView findByWeekId(@PathVariable Long id, Authentication authentication) {
        UserDto dto = getCurrentUser(authentication);
        ModelAndView model = new ModelAndView("match");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("currentUser", dto);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("header", "Матчи " + id + " тура");
        model.addObject("matchList", service.getAllByWeekId(id));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping("/week/current")
    public ModelAndView findByCurrentWeek(Authentication authentication) {
        UserDto dto = getCurrentUser(authentication);
        ModelAndView model = new ModelAndView("match");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("currentUser", dto);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("header", "Матчи " + weekService.getCurrentWeek().getId() + " тура");
        model.addObject("matchList", service.getAllByCurrentWeek(true));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping("/{id}")
    public MatchDto findById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/byNames")
    public MatchDto findByTeamNames(@RequestParam String home, @RequestParam String away) {
        return service.getByTeamNames(home, away);
    }

    @GetMapping("/byCodes")
    public MatchDto findByTeamCodes(@RequestParam String home, @RequestParam String away) {
        return service.getByTeamCodes(home, away);
    }

    @GetMapping("/result/byNames")
    public List<Integer> getResultByTeamNames(@RequestParam String homeTeamName, @RequestParam String awayTeamName) {
        return service.getResultByTeamNames(homeTeamName, awayTeamName);
    }

    @GetMapping("/today")
    public ModelAndView findTodayMatches(Authentication authentication) {
        UserDto dto = getCurrentUser(authentication);
        ModelAndView model = new ModelAndView("match");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("currentUser", dto);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("header", "Матчи сегодня");
        model.addObject("matchList", service.getAllByTodayDate());
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping("/upcoming")
    public ModelAndView findUpcomingMatches(@RequestParam Integer days, Authentication authentication) {
        UserDto dto = getCurrentUser(authentication);
        ModelAndView model = new ModelAndView("match");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("currentUser", dto);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("header", "Матчи в ближайшие дни - " + days);
        model.addObject("matchList", service.getAllByUpcomingDays(days));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    public UserDto getCurrentUser(Authentication a) {
        User user = (User) a.getPrincipal();
        return UserDto.builder().id(user.getId()).login(user.getLogin()).build();
    }
}
