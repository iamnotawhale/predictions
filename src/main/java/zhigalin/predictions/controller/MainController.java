package zhigalin.predictions.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.securirty.PersonDetails;
import zhigalin.predictions.dto.event.HeadToHeadDto;
import zhigalin.predictions.dto.event.MatchDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.service.event.HeadToHeadService;
import zhigalin.predictions.service.event.MatchService;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.news.NewsService;
import zhigalin.predictions.service.predict.PredictionService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        List<List<HeadToHeadDto>> listOfHeadToHeads = new ArrayList<>();
        List<MatchDto> allByCurrentWeek = matchService.getAllByCurrentWeek(true);
        for (MatchDto matchDto : allByCurrentWeek) {
            List<HeadToHeadDto> allByMatch = headToHeadService.getAllByMatch(matchDto);
            listOfHeadToHeads.add(allByMatch);
        }

        ModelAndView model = new ModelAndView("main");
        model.addObject("map", predictionService.allUsersPoints());
        model.addObject("todayDateTime", LocalDateTime.now());
        model.addObject("matchList", allByCurrentWeek);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("h2h", listOfHeadToHeads);
        model.addObject("online", matchService.getOnline());
        model.addObject("standings", standingService.getAll());
        model.addObject("news", newsService.getLastNews());

        return model;
    }

    @ModelAttribute("currentUser")
    public UserDto getCurrentUser() {
        PersonDetails personDetails = (PersonDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        return UserDto.builder()
                .id(personDetails.user().getId())
                .login(personDetails.user().getLogin())
                .build();
    }
}
