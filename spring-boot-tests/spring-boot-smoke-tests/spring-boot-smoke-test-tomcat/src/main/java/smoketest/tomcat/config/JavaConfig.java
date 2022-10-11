package smoketest.tomcat.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy
// @EnableAsync
public class JavaConfig {

    @Bean(name = "messageSource")
    public MessageSource getMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        // 依旧中文乱码，检查 message.properties 的文件编码
        messageSource.setDefaultEncoding("UTF-8");
        // messageSource.setCacheSeconds(10);

        /** {@link org.springframework.context.support.AbstractResourceBasedMessageSource#basenameSet} */
        messageSource.addBasenames(
                "message",                  // resources/message.properties
                "message_en"                // resources/message_en.properties
        );
        return messageSource;
    }

    @Bean
    public TransactionManager transactionManager() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:mysql://rm-bp150s48920cjevsweo.mysql.rds.aliyuncs.com:3306/test");
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUsername("haisen");
        dataSource.setPassword("Haisen123");
        return new DataSourceTransactionManager(dataSource);
    }
}