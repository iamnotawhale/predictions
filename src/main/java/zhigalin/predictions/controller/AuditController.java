package zhigalin.predictions.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import zhigalin.predictions.model.user.User;
import zhigalin.predictions.service.audit.AuditService;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    @Autowired
    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/list")
    public ModelAndView getAllAudits(Authentication authentication) {
        User user = (User) authentication.getPrincipal();

        ModelAndView model = new ModelAndView("audit");
        model.addObject("pred", auditService.getAllPredictedAuditions());
        model.addObject("date", auditService.getDateOfChanges());
        model.addObject("mode", auditService.getModes());
        model.addObject("currentUser", user);
        model.addObject("header", "Список изменений");

        return model;
    }

    @GetMapping
    public String getListObject() {
        return auditService.example();
    }
}
