package com.cloudstorage.blockserver.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange fileChangeExchange() {
        return new TopicExchange("file-change-exchange");
    }
}
