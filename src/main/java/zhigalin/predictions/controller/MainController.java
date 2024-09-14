//package zhigalin.predictions.controller;
//
//import java.io.IOException;
//import java.text.ParseException;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.stream.Stream;
//
//import com.rometools.rome.io.FeedException;
//import lombok.RequiredArgsConstructor;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.ModelAttribute;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.servlet.ModelAndView;
//import zhigalin.predictions.model.event.Match;
//import zhigalin.predictions.model.user.User;
//import zhigalin.predictions.service.DataInitService;
//import zhigalin.predictions.service.event.HeadToHeadService;
//import zhigalin.predictions.service.event.MatchService;
//import zhigalin.predictions.service.predict.PredictionService;
//import zhigalin.predictions.service.user.UserService;
//import zhigalin.predictions.util.DaoUtil;
//
//@RequiredArgsConstructor
//@RestController
//@RequestMapping
//public class MainController {
//
//    private final MatchService matchService;
//    private final HeadToHeadService headToHeadService;
//    private final DataInitService dataInitService;
//    private final UserService userService;
//    private final PredictionService predictionService;
//
//    @GetMapping()
//    public ModelAndView getMainPage() throws FeedException, IOException, ParseException {
//        ModelAndView model = new ModelAndView("main");
//        List<Match> onlineMatches = matchService.findOnlineMatches();
//        model.addObject("map", predictionService.getAllPointsByUsers());
//        model.addObject("todayDateTime", LocalDateTime.now());
//        model.addObject("currentWeek", DaoUtil.currentWeekId);
//        model.addObject("matchList", matchService.findAllByCurrentWeek());
//        model.addObject("h2h", headToHeadService.findAllByCurrentWeekNew());
//        model.addObject("onlineTeamsIds", onlineMatches.stream()
//                .flatMap(match -> Stream.of(match.getHomeTeamId(), match.getAwayTeamId()))
//                .toList()
//        );
//        model.addObject("onlineMatches", onlineMatches);
//        model.addObject("standings", matchService.getStandings());
//        model.addObject("news", dataInitService.newsInit());
//        model.addObject("teams", DaoUtil.TEAMS);
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
//}
