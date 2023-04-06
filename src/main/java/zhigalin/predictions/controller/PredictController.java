package zhigalin.predictions.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.config.UserDetailsImpl;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.dto.user.UserDto;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.football.StandingService;
import zhigalin.predictions.service.predict.PointsService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/predict")
public class PredictController {
    private final PredictionService service;
    private final UserService userService;
    private final WeekService weekService;
    private final StandingService standingService;
    private final PointsService pointsService;

    @GetMapping("/match/{id}")
    public List<PredictionDto> getByMatchId(@PathVariable Long id) {
        return service.findAllByMatchId(id);
    }

    @GetMapping("/{id}")
    public PredictionDto getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping("/saveAndUpdate")
    public ModelAndView saveAndUpdate(@ModelAttribute PredictionDto dto, HttpServletRequest request) {
        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
        service.save(dto);
        return model;
    }

    @GetMapping("/delete")
    public ModelAndView deletePredict(@RequestParam Long id, HttpServletRequest request) {
        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
        service.deleteById(id);
        return model;
    }

    @GetMapping("/week/{id}")
    public ModelAndView getByWeekId(@PathVariable Long id) {
        ModelAndView model = new ModelAndView("predict");
        model.addObject("weeklyUsersPoints", pointsService.getWeeklyUsersPoints(id));
        model.addObject("header", "Прогнозы " + id + " тура");
        model.addObject("list", service.findAllByWeekId(id));
        return model;
    }

    @GetMapping("/byUserAndWeek")
    public ModelAndView getByUserAndWeek(@RequestParam Long user, @RequestParam Long week) {
        ModelAndView model = new ModelAndView("predict");
        model.addObject("header", "Прогнозы " + userService.findById(user).getLogin() + " " + week + " тура");
        model.addObject("list", service.findAllByUserIdAndWeekId(user, week));
        model.addObject("newPredict", PredictionDto.builder().build());
        return model;
    }


    @GetMapping("/week")
    public ModelAndView getByCurrentUserAndWeek(@RequestParam Long id) {
        ModelAndView model = new ModelAndView("predict");
        model.addObject("header", "Мои прогнозы " + id + " тура");
        model.addObject("list", service.findAllByUserIdAndWeekId(getCurrentUser().getId(), id));
        model.addObject("newPredict", PredictionDto.builder().build());
        return model;
    }

    @GetMapping()
    public ModelAndView getByUser() {
        ModelAndView model = new ModelAndView("predict");
        model.addObject("header", "Мои прогнозы ");
        model.addObject("list", service.findAllByUserId(getCurrentUser().getId()));
        model.addObject("newPredict", PredictionDto.builder().build());
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
    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.now().minusMinutes(5L);
    }

    @ModelAttribute("places")
    public Map<Long, Integer> places() {
        return standingService.getPlaces();
    }
}
