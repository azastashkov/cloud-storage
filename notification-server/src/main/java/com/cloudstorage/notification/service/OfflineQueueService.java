package com.cloudstorage.notification.service;

import com.cloudstorage.common.event.FileChangeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OfflineQueueService {

    private static final String OFFLINE_KEY_PREFIX = "cloud:offline:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.offline-ttl-days}")
    private int offlineTtlDays;

    public OfflineQueueService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void queueForOfflineUser(UUID userId, FileChangeEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            String key = OFFLINE_KEY_PREFIX + userId;
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, (long) offlineTtlDays * 86400, TimeUnit.SECONDS);
            log.info("Queued offline notification for user {}: {}", userId, event.eventType());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize FileChangeEvent for offline user {}", userId, e);
        }
    }

    public List<FileChangeEvent> drainOfflineQueue(UUID userId) {
        String key = OFFLINE_KEY_PREFIX + userId;
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
                log.error("Failed to deserialize FileChangeEvent for offline user {}", userId, e);
            }
        }
        return events;
    }

    public long getQueueDepth(UUID userId) {
        Long length = redisTemplate.opsForList().size(OFFLINE_KEY_PREFIX + userId);
        return length != null ? length : 0L;
    }
}
