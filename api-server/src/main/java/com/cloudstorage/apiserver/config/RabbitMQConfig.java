package com.cloudstorage.apiserver.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange fileChangeExchange() {
        return new TopicExchange("file-change-exchange");
    }

    @Bean
    public Queue fileChangeQueue() {
        return QueueBuilder.durable("file-change-notifications").build();
    }

    @Bean
    public Binding binding(Queue fileChangeQueue, TopicExchange fileChangeExchange) {
        return BindingBuilder.bind(fileChangeQueue).to(fileChangeExchange).with("file.change.#");
    }
}
