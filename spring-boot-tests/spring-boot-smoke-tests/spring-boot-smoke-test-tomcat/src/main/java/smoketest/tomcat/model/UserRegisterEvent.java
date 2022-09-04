package smoketest.tomcat.model;

import org.springframework.context.ApplicationEvent;

// 1. 实现自定义事件

public class UserRegisterEvent extends ApplicationEvent {
    public String username;

    // source：获取事件源，username：事件内容
    public UserRegisterEvent(Object source, String username) {
        super(source);
        this.username = username;
    }
}