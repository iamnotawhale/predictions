package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.TeamService;

@RequiredArgsConstructor
@RestController
@RequestMapping("/team")
public class TeamController {

    private final TeamService service;
    private final MatchService matchService;
    private final WeekService weekService;

    @PostMapping("/save")
    public TeamDto saveTeam(@RequestBody TeamDto teamDto) {
        return service.saveTeam(teamDto);
    }

    @GetMapping("/{id}")
    public ModelAndView getTeamById(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ModelAndView model = new ModelAndView("team");
        model.addObject("header", service.getById(id).getTeamName());
        model.addObject("currentUser", user);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("last5", matchService.getLast5MatchesByTeamId(id));
        model.addObject("last5Result", matchService.getLast5MatchesResultByTeamId(id));
        return model;
    }

    @GetMapping("/byName")
    public TeamDto findByTeamName(@RequestParam String name) {
        return service.getByName(name);
    }

    @GetMapping("/byCode")
    public TeamDto findByTeamCode(@RequestParam String code) {
        return service.getByCode(code);
    }
}
