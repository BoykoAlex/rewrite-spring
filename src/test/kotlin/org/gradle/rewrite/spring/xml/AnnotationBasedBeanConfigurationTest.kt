package org.gradle.rewrite.spring.xml

import com.netflix.rewrite.Parser
import org.gradle.rewrite.spring.assertRefactored
import org.junit.jupiter.api.Test

class AnnotationBasedBeanConfigurationTest: Parser() {
    @Test
    fun parseXmlConfiguration() {
        val repository = """
            package repositories;
            public class UserRepository {
            }
        """.trimIndent()

        val service = parse("""
            package services;
            
            import repositories.*;
            
            public class UserService {
                private UserRepository userRepository;
                private int maxUsers;
                
                public void setUserRepository(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }
                
                public void onInit() {
                }
                
                public void onDestroy() {
                }
                
                public void setMaxUsers(int maxUsers) {
                    this.maxUsers = maxUsers;
                }
            }
        """.trimIndent(), repository)

        val beansXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <beans xmlns="http://www.springframework.org/schema/beans"
            	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:context="http://www.springframework.org/schema/context"
            	xmlns:aop="http://www.springframework.org/schema/aop"
            	xsi:schemaLocation="http://www.springframework.org/schema/beans
                http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
                ">
             
                <bean id="userRepository" class="repositories.UserRepository"/>
            	<bean id="userService" class="services.UserService" lazy-init="true" 
                        scope="prototype" init-method="onInit" destroy-method="onDestroy">
                    <property name="userRepository" ref="userRepository"/>
                    <property name="maxUsers" value="1000"/>
            	</bean>
            </beans>
        """.trimIndent()

        val fixed = service.refactor().visit(AnnotationBasedBeanConfiguration(beansXml.byteInputStream()))
                .fix().fixed

        assertRefactored(fixed, """
            package services;

            import javax.annotation.PostConstruct;
            import javax.annotation.PreDestroy;
            import org.springframework.beans.factory.annotation.Autowired;
            import org.springframework.beans.factory.annotation.Value;
            import org.springframework.beans.factory.config.ConfigurableBeanFactory;
            import org.springframework.context.annotation.Lazy;
            import org.springframework.context.annotation.Scope;
            import org.springframework.stereotype.Component;
            import repositories.*;

            @Component
            @Lazy
            @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
            public class UserService {
                @Autowired
                private UserRepository userRepository;
            
                @Value(1000)
                private int maxUsers;
                
                public void setUserRepository(UserRepository userRepository) {
                    this.userRepository = userRepository;
                }
                
                @PostConstruct
                public void onInit() {
                }
                
                @PreDestroy
                public void onDestroy() {
                }
                
                public void setMaxUsers(int maxUsers) {
                    this.maxUsers = maxUsers;
                }
            }
        """)
    }
}