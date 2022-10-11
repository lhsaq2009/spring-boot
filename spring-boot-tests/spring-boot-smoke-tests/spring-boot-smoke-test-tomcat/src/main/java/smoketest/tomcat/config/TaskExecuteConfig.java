package smoketest.tomcat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class TaskExecuteConfig {

    final public static String POOL_TASK = "Pool-Task";

    @Bean(name = POOL_TASK)
    public ThreadPoolTaskExecutor taskAsyncPool() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);                        // 线程池最少预留线程数
        executor.setMaxPoolSize(8);                         // 线程池存留最大线程数
        executor.setQueueCapacity(100);                     // 任务队列最多 100 个
        executor.setKeepAliveSeconds(360);                  // 超过 6 分钟被清理

        executor.setThreadNamePrefix(POOL_TASK + "_");      // 线程前缀名字
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()   // 由「调⽤者线程」来处理该任务；
        );


        executor.initialize();

        return executor;
    }
}