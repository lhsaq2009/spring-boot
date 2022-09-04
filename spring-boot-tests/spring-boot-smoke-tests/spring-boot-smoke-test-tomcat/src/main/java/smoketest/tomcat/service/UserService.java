package smoketest.tomcat.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smoketest.tomcat.model.UserRegisterEvent;

@Service
public class UserService implements ApplicationEventPublisherAware {

    // 2. 事件发布者 ApplicationEventPublisher：通过它，进行事件的发布。
    private ApplicationEventPublisher publisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * 在完成自身的用户注册逻辑之后，仅仅只需要发布一个 UserRegisterEvent 事件，而无需关注其它拓展逻辑。
     * 其它 Service 可以自己订阅 UserRegisterEvent 事件，实现自定义的拓展逻辑。
     */
    @Transactional
    public void register(String username) {
        System.out.println("[register] 执行用户(" + username + ") 的注册逻辑");
        publisher.publishEvent(new UserRegisterEvent(this, username));
        // throw new RuntimeException();
    }
}