package smoketest.tomcat.service;

import jodd.util.ThreadUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import smoketest.tomcat.config.TaskExecuteConfig;

import javax.annotation.Resource;

/**
 * <p>〈功能概述〉.
 *
 * @author haisen /20229/5
 */
@Service
@EnableAsync(                           // 一般放在 Spring Main 启动类上，开启异步功能
        // mode = AdviceMode.PROXY      // 默认
        // mode = AdviceMode.ASPECTJ    // 实现类内部自调用也可以异步
)
public class TaskPoolService {

    @Resource
    @Qualifier(TaskExecuteConfig.POOL_TASK)
    private ThreadPoolTaskExecutor threadPool;

    /*
     * @Async：异步方法，会另起新线程执行；避免阻塞、以及保证任务的实时性。适用于：异步处理日志、发送邮件、短信... 等
     * @Async：如果用在类上，则该类所有的方法都是异步执行的
     * @Async：若不配置线程池名称，默认：org.springframework.core.task.SimpleAsyncTaskExecutor 线程池（core size = 4）
     */
    @Async(value = TaskExecuteConfig.POOL_TASK)
    public void taskA() {
        ThreadUtil.sleep(2_000);
        System.out.println(Thread.currentThread().getName() + " taskA() Done");
    }

    public void taskB() {
        threadPool.execute(() -> {
            ThreadUtil.sleep(2_000);
            System.out.println(Thread.currentThread().getName() + " taskB() Done");
        });
    }
}
