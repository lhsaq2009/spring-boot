package smoketest.tomcat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import smoketest.tomcat.model.UserRegisterEvent;

import java.util.Date;

/**
 * <p>〈功能概述〉.
 *
 * @author haisen /20229/4
 */
@Service
// @EnableAsync
public class UserEmailAsyncService {

    // 异步执行，另起新线程执行，需要 AOP 创建代理类
    // @Async
    // 问：若启用异步处理，这里需要数据库查询新注册用户信息，使用 @TransactionalEventListener 等待主逻辑事务结束再异步执行
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void userRegisterAsync(UserRegisterEvent event) throws InterruptedException {
        System.out.println("方式六：收到时间通知，开始处理");
        Thread.sleep(5 * 1000);

        System.out.println("方式六：异步处理：" + event.username + "，" + new Date() + "，Thread=" + Thread.currentThread().getName());
    }
}
