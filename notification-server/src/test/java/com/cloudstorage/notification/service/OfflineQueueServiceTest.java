package com.cloudstorage.notification.service;

import com.cloudstorage.common.event.FileChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineQueueServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    private OfflineQueueService offlineQueueService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        offlineQueueService = new OfflineQueueService(redisTemplate);

        Field ttlField = OfflineQueueService.class.getDeclaredField("offlineTtlDays");
        ttlField.setAccessible(true);
        ttlField.setInt(offlineQueueService, 7);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void queueForOfflineUser_callsRpushAndExpire() throws Exception {
        UUID userId = UUID.randomUUID();
        FileChangeEvent event = new FileChangeEvent(
                UUID.randomUUID(), "/test.txt", "upload", 1, userId, Instant.now()
        );
        String expectedJson = objectMapper.writeValueAsString(event);
        String key = "cloud:offline:" + userId;

        when(redisTemplate.expire(key, 7L * 86400, TimeUnit.SECONDS)).thenReturn(true);

        offlineQueueService.queueForOfflineUser(userId, event);

        verify(listOperations).rightPush(key, expectedJson);
        verify(redisTemplate).expire(key, 7L * 86400, TimeUnit.SECONDS);
    }

    @Test
    void drainOfflineQueue_getsItemsAndDeletesKey() throws Exception {
        UUID userId = UUID.randomUUID();
        FileChangeEvent event = new FileChangeEvent(
                UUID.randomUUID(), "/test.txt", "upload", 1, userId, Instant.now()
        );
        String json = objectMapper.writeValueAsString(event);
        String key = "cloud:offline:" + userId;

        when(listOperations.range(key, 0, -1)).thenReturn(List.of(json));
        when(redisTemplate.delete(key)).thenReturn(true);

        List<FileChangeEvent> result = offlineQueueService.drainOfflineQueue(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileId()).isEqualTo(event.fileId());
        assertThat(result.get(0).path()).isEqualTo("/test.txt");
        verify(listOperations).range(key, 0, -1);
        verify(redisTemplate).delete(key);
    }

    @Test
    void drainOfflineQueue_returnsEmptyListWhenNoItems() {
        UUID userId = UUID.randomUUID();
        String key = "cloud:offline:" + userId;

        when(listOperations.range(key, 0, -1)).thenReturn(null);
        when(redisTemplate.delete(key)).thenReturn(true);

        List<FileChangeEvent> result = offlineQueueService.drainOfflineQueue(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getQueueDepth_callsLlen() {
        UUID userId = UUID.randomUUID();
        String key = "cloud:offline:" + userId;

        when(listOperations.size(key)).thenReturn(5L);

        long result = offlineQueueService.getQueueDepth(userId);

        assertThat(result).isEqualTo(5L);
        verify(listOperations).size(key);
    }

    @Test
    void getQueueDepth_returnsZeroWhenNull() {
        UUID userId = UUID.randomUUID();
        String key = "cloud:offline:" + userId;

        when(listOperations.size(key)).thenReturn(null);

        long result = offlineQueueService.getQueueDepth(userId);

        assertThat(result).isEqualTo(0L);
    }
}
