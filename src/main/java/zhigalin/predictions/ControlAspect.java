package zhigalin.predictions;

import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
public class ControlAspect {

    @Pointcut("@annotation(zhigalin.predictions.Control)")
    public void annotated() {

    }

    @Before("annotated()")
    public void beforeAnnotated() {
        System.out.println("BEFORE");
    }

    @Transactional()
    @After("annotated()")
    public void afterAnnotated() {
        System.out.println("AFTER");
    }
}
