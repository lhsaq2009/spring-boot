package smoketest.tomcat.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

// 必须这个名字：AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster"
@Component("applicationEventMulticaster")
public class MyApplicationEventMulticaster
        extends SimpleApplicationEventMulticaster implements InitializingBean {

    @Resource
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Override
    public void afterPropertiesSet() throws Exception {
        setTaskExecutor(threadPoolTaskExecutor);
        // TODO：new SimpleAsyncTaskExecutor();
    }
}
