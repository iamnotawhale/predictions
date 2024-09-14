//package zhigalin.predictions.controller;
//
//
//import java.time.LocalDateTime;
//import java.util.Map;
//
//import jakarta.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.ModelAttribute;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.servlet.ModelAndView;
//import zhigalin.predictions.model.football.Team;
//import zhigalin.predictions.model.predict.Prediction;
//import zhigalin.predictions.model.user.User;
//import zhigalin.predictions.service.event.MatchService;
//import zhigalin.predictions.service.predict.PredictionService;
//import zhigalin.predictions.service.user.UserService;
//import zhigalin.predictions.util.DaoUtil;
//
//@RequiredArgsConstructor
//@RestController
//@RequestMapping("/predict")
//public class PredictController {
//    private final PredictionService predictionService;
//    private final UserService userService;
//    private final MatchService matchService;
//
//    @PostMapping("/saveAndUpdate")
//    public ModelAndView saveAndUpdate(@ModelAttribute Prediction prediction, HttpServletRequest request) {
//        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
//        predictionService.save(prediction);
//        return model;
//    }
//
//    @GetMapping("/delete")
//    public ModelAndView deletePredict(@RequestParam int userId, @RequestParam int matchPublicId, HttpServletRequest request) {
//        ModelAndView model = new ModelAndView("redirect:" + request.getHeader("referer"));
//        predictionService.deleteById(userId, matchPublicId);
//        return model;
//    }
//
//    @GetMapping("/week/{id}")
//    public ModelAndView getByWeekId(@PathVariable int id) {
//        ModelAndView model = new ModelAndView("predict");
//        model.addObject("weeklyUsersPoints", predictionService.getWeeklyUsersPoints(id));
//        model.addObject("header", "Прогнозы " + id + " тура");
//        model.addObject("list", predictionService.findAllByWeekId(id));
//        return model;
//    }
//
//    @GetMapping("/byUserAndWeek")
//    public ModelAndView getByUserAndWeek(@RequestParam int userId, @RequestParam int weekId) {
//        ModelAndView model = new ModelAndView("predict");
//        model.addObject("header", "Прогнозы " + userService.findById(userId).getLogin() + " " + weekId + " тура");
//        model.addObject("list", predictionService.findAllByUserIdAndWeekId(userId, weekId));
//        model.addObject("newPredict", Prediction.builder().build());
//        return model;
//    }
//
//
//    @GetMapping("/week")
//    public ModelAndView getByCurrentUserAndWeek(@RequestParam int weekId) {
//        ModelAndView model = new ModelAndView("predict");
//        model.addObject("header", "Мои прогнозы " + weekId + " тура");
//        model.addObject("list", predictionService.getPredictionsByUserAndWeek(getCurrentUser().getId(), weekId));
//        model.addObject("newPredict", Prediction.builder().build());
//        return model;
//    }
//
//    @GetMapping()
//    public ModelAndView getByUser() {
//        ModelAndView model = new ModelAndView("predict");
//        model.addObject("header", "Мои прогнозы ");
//        model.addObject("list", predictionService.findAllByUserId(getCurrentUser().getId()));
//        model.addObject("newPredict", Prediction.builder().build());
//        return model;
//    }
//
//    @ModelAttribute("currentUser")
//    public User getCurrentUser() {
//        UserDetails userDetails = (UserDetails) SecurityContextHolder
//                .getContext()
//                .getAuthentication()
//                .getPrincipal();
//
//        return userService.findByLogin(userDetails.getUsername());
//    }
//
//    @ModelAttribute("currentWeek")
//    public Integer getCurrentWeekId() {
//        return DaoUtil.currentWeekId;
//    }
//
//    @ModelAttribute("todayDateTime")
//    public LocalDateTime getLocalDateTime() {
//        return LocalDateTime.now().minusMinutes(5L);
//    }
//
//    @ModelAttribute("places")
//    public Map<Integer, Integer> places() {
//        return matchService.getPlaces();
//    }
//
//    @ModelAttribute("teams")
//    public Map<Integer, Team> teams() {
//        return DaoUtil.TEAMS;
//    }
//
//    @ModelAttribute("users")
//    public Map<Integer, User> users() {
//        return DaoUtil.USERS;
//    }
//}
