package smoketest.tomcat.service;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import smoketest.tomcat.model.UserRegisterEvent;

// 3. 事件监听器 ApplicationListener：通过实现它，进行指定类型的事件的监听。
@Service
public class EmailService /*implements ApplicationListener<UserRegisterEvent>*/ {
    
    // @Async 声明异步执行，另起新线程执行，毕竟实际场景下，发送邮件可能比较慢，又是非关键逻辑。

    // @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationEvent(UserRegisterEvent event) {
        // 问题：如果如果异步处理，这里需要数据库查询新注册用户信息，可能因为主逻辑事务还未结束，而查询不到。
        // 请了解注解：org.springframework.transaction.event.TransactionalEventListener

        System.out.println("[onApplicationEvent] 给用户(" + event.username + ") 发送邮件");
    }
}
