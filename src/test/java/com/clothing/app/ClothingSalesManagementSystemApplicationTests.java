package com.clothing.app;

import com.clothing.app.config.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "app.demo-data.enabled=false")
class ClothingSalesManagementSystemApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertTrue(context.getBeansOfType(DataInitializer.class).isEmpty());
    }

}
