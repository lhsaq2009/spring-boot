/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package smoketest.tomcat.web;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import smoketest.tomcat.Student;
import smoketest.tomcat.service.HelloWorldService;
import smoketest.tomcat.service.UserService;

import javax.annotation.Resource;

@Controller
public class SampleController implements ApplicationContextAware {

    @Autowired
    private HelloWorldService helloWorldService;

    private ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }

    @GetMapping("/")
    @ResponseBody
    public String helloWorld() {
        // return this.helloWorldService.getHelloMessage();

        Student student = (Student) ctx.getBean("student");
        return student.getName();
    }

    @Resource
    private UserService userService;

    @GetMapping("/register")
    @ResponseBody
    public String register(String username) {
        try {
            userService.register(username);
        } catch (Exception e) {
            System.out.println("userService.register 发生异常：" + e.getMessage());
        }
        return "success";
    }
}
