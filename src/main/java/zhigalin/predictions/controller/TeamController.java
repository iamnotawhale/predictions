package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.dto.football.TeamDto;
import zhigalin.predictions.dto.user.UserDto;
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
        return service.save(teamDto);
    }

    @GetMapping("/{id}")
    public ModelAndView getTeamById(@PathVariable Long id) {
        ModelAndView model = new ModelAndView("team");
        model.addObject("header", service.findById(id).getTeamName());
        model.addObject("currentWeek", weekService.findCurrentWeek().getId());
        model.addObject("last5", matchService.findLast5MatchesByTeamId(id));
        model.addObject("last5Result", matchService.getLast5MatchesResultByTeamId(id));
        return model;
    }

    @GetMapping("/byName")
    public TeamDto findByTeamName(@RequestParam String name) {
        return service.findByName(name);
    }

    @GetMapping("/byCode")
    public TeamDto findByTeamCode(@RequestParam String code) {
        return service.findByCode(code);
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
