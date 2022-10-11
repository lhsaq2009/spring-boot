package smoketest.aop.monitor;

import jodd.util.ThreadUtil;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Date;

@Aspect
@Component
public class ServiceTimeHandler {

    @Before("execution(* smoketest.aop.service.HelloWorldService.*(..))")
    public void printTime_Before() {
        ThreadUtil.sleep(100);
        System.out.println("CurrentTime_1:" + new Date());
    }

    @After("execution(* smoketest.aop.service.HelloWorldService.*(..))")
    public void printTime_After() {
        ThreadUtil.sleep(100);
        System.out.println("CurrentTime_2:" + new Date());
    }

}