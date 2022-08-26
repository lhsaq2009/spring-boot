package smoketest.tomcat;


import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * 生命周期的一些方法，{@link AbstractAutowireCapableBeanFactory#initializeBean(String, Object, RootBeanDefinition)}
 */
public class Man implements BeanPostProcessor, BeanNameAware/*, ApplicationContextAware*/ {

    private String beanName;


    // 在调用 init-method 之前执行
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("postProcessBeforeInitialization");
        return bean;
    }

    // 在调用 init-method 之后执行
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("postProcessAfterInitialization");
        return bean;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;                       // 回调传给我用
    }

    /*
     * private ApplicationContext context;
     *
     * 这个应该在 Web 环境下使用：
     * import org.springframework.context.ApplicationContext;
     * import org.springframework.context.ApplicationContextAware;
     *
     * @Override
     * public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
     *     this.context = applicationContext;          // 回调传给我用
     * }
     */
}
