package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.dto.predict.PredictionDto;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.event.WeekService;
import zhigalin.predictions.service.predict.PredictionService;
import zhigalin.predictions.service.user.UserService;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/predict")
public class PredictController {

    private final PredictionService predictionService;
    private final UserService userService;
    private final WeekService weekService;

    @Autowired
    public PredictController(PredictionService predictionService, UserService userService, WeekService weekService) {
        this.predictionService = predictionService;
        this.userService = userService;
        this.weekService = weekService;
    }

    @GetMapping("/match/{id}")
    public List<PredictionDto> getByMatchId(@PathVariable Long id) {
        return predictionService.getAllByMatchId(id);
    }

    @GetMapping("/{id}")
    public PredictionDto getById(@PathVariable Long id) {
        return predictionService.getById(id);
    }

    @PostMapping("/saveAndUpdate")
    public ModelAndView saveAndUpdate(@ModelAttribute PredictionDto dto, HttpServletRequest request) {
        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
        predictionService.save(dto);
        return model;
    }

    @GetMapping("/delete")
    public ModelAndView deletePredict(@RequestParam Long id, HttpServletRequest request) {
        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
        predictionService.deleteById(id);
        return model;
    }

    @GetMapping("/week/{id}")
    public ModelAndView getByWeekId(@PathVariable Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ModelAndView model = new ModelAndView("predict");
        model.addObject("weeklyUsersPoints", predictionService.usersPointsByWeek(id));
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("header", "Прогнозы " + id + " тура");
        model.addObject("currentUser", user);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("list", predictionService.getAllByWeekId(id));
        return model;
    }

    @GetMapping("/byUserAndWeek")
    public ModelAndView getByUserAndWeek(@RequestParam Long user, @RequestParam Long week, Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        ModelAndView model = new ModelAndView("predict");
        model.addObject("header", "Прогнозы " + userService.getById(user).getLogin() + " " + week + " тура");
        model.addObject("list", predictionService.getAllByUserIdAndWeekId(user, week));
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("newPredict", new PredictionDto());
        model.addObject("currentUser", currentUser);
        return model;
    }


    @GetMapping("/week")
    public ModelAndView getByCurrentUserAndWeek(@RequestParam Long id, Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("header", "Мои прогнозы " + id + " тура");
        model.addObject("currentUser", user);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("list", predictionService.getAllByUserIdAndWeekId(user.getId(), id));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }

    @GetMapping()
    public ModelAndView getByUser(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        ModelAndView model = new ModelAndView("predict");
        model.addObject("todayDateTime", LocalDateTime.now().minusMinutes(5L));
        model.addObject("header", "Мои прогнозы ");
        model.addObject("currentUser", user);
        model.addObject("currentWeek", weekService.getCurrentWeekId());
        model.addObject("list", predictionService.getAllByUser_Id(user.getId()));
        model.addObject("newPredict", new PredictionDto());
        return model;
    }
}
