package com.live;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestMediaConfig {
    @Bean
    @Primary
    public FakeMediaServer fakeMediaServer() {
        return new FakeMediaServer();
    }

    @Bean
    @Primary
    public FakePaymentGateway fakePaymentGateway() {
        return new FakePaymentGateway();
    }
}
