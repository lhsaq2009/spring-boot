package smoketest.tomcat.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smoketest.tomcat.model.UserRegisterEvent;

import java.util.Date;

/**
 * <p>〈功能概述〉.
 *
 * @author haisen /20229/4
 */
@Service
public class UserRegisterService {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Transactional
    public void publishEventWithTransactional(String username) {
        publisher.publishEvent(new UserRegisterEvent(publisher, username));
        // ThreadUtil.sleep(10_000);
        System.out.println("publishEvent " + new Date());
        // throw new RuntimeException();       // 如果取消注释，造成本事务失败，那么 toSendEmail() 也就不执行了。
    }
}
