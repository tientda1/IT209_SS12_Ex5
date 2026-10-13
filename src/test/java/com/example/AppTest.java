package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AppTest {

    @Autowired
    private HelloController helloController;

    @Test
    void contextLoadsAndHelloEndpointResponds() {
        assertEquals("Hello from Spring Boot CI demo!", helloController.hello());
    }
}
