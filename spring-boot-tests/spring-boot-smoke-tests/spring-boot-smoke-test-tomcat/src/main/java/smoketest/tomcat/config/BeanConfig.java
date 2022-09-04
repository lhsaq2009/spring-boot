package smoketest.tomcat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionManager;

/**
 * <p>〈功能概述〉.
 *
 * @author haisen /20229/4
 */

public class BeanConfig {

    /*@Bean("transactionManager")
    public TransactionManager transactionManager() {

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:mysql://rm-bp150s48920cjevsweo.mysql.rds.aliyuncs.com:3306/mybatis");
        dataSource.setUsername("root");
        dataSource.setPassword("Haisen123");

        return new DataSourceTransactionManager(dataSource);
    }*/

}
