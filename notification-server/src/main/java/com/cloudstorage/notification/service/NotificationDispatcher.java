package com.cloudstorage.notification.service;

import com.cloudstorage.common.event.FileChangeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class NotificationDispatcher {

    private static final String ONLINE_USERS_KEY = "cloud:online:users";
    private static final String PENDING_KEY_PREFIX = "cloud:pending:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationDispatcher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void markUserOnline(UUID userId) {
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId.toString());
        log.info("User {} marked as online", userId);
    }

    public void markUserOffline(UUID userId) {
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId.toString());
        log.info("User {} marked as offline", userId);
    }

    public boolean isUserOnline(UUID userId) {
        Boolean isMember = redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    public void addPendingNotification(UUID userId, FileChangeEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.opsForList().rightPush(PENDING_KEY_PREFIX + userId, json);
            log.debug("Added pending notification for user {}: {}", userId, event.eventType());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize FileChangeEvent for user {}", userId, e);
        }
    }

    public List<FileChangeEvent> drainPendingNotifications(UUID userId) {
        String key = PENDING_KEY_PREFIX + userId;
        List<String> items = redisTemplate.opsForList().range(key, 0, -1);
        redisTemplate.delete(key);

        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<FileChangeEvent> events = new ArrayList<>();
        for (String json : items) {
            try {
                events.add(objectMapper.readValue(json, FileChangeEvent.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize FileChangeEvent for user {}", userId, e);
            }
        }
        return events;
    }
}
