package com.cloudstorage.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange fileChangeExchange() {
        return new TopicExchange("file-change-exchange");
    }

    @Bean
    public Queue fileChangeNotificationQueue() {
        return QueueBuilder.durable("file-change-notifications").build();
    }

    @Bean
    public Queue offlineBackupQueue() {
        return QueueBuilder.durable("offline-backup-queue").build();
    }

    @Bean
    public Queue fileChangeDlq() {
        return QueueBuilder.durable("file-change-dlq").build();
    }

    @Bean
    public Binding notificationBinding(Queue fileChangeNotificationQueue, TopicExchange fileChangeExchange) {
        return BindingBuilder.bind(fileChangeNotificationQueue).to(fileChangeExchange).with("file.change.#");
    }

    @Bean
    public Binding offlineBinding(Queue offlineBackupQueue, TopicExchange fileChangeExchange) {
        return BindingBuilder.bind(offlineBackupQueue).to(fileChangeExchange).with("file.change.#");
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
