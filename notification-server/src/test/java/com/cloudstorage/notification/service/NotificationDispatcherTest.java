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
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ListOperations<String, String> listOperations;

    private NotificationDispatcher dispatcher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        dispatcher = new NotificationDispatcher(redisTemplate);

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void markUserOnline_callsSadd() {
        UUID userId = UUID.randomUUID();

        dispatcher.markUserOnline(userId);

        verify(setOperations).add("cloud:online:users", userId.toString());
    }

    @Test
    void markUserOffline_callsSrem() {
        UUID userId = UUID.randomUUID();

        dispatcher.markUserOffline(userId);

        verify(setOperations).remove("cloud:online:users", userId.toString());
    }

    @Test
    void isUserOnline_returnsTrueWhenMember() {
        UUID userId = UUID.randomUUID();
        when(setOperations.isMember("cloud:online:users", userId.toString())).thenReturn(true);

        boolean result = dispatcher.isUserOnline(userId);

        assertThat(result).isTrue();
        verify(setOperations).isMember("cloud:online:users", userId.toString());
    }

    @Test
    void isUserOnline_returnsFalseWhenNotMember() {
        UUID userId = UUID.randomUUID();
        when(setOperations.isMember("cloud:online:users", userId.toString())).thenReturn(false);

        boolean result = dispatcher.isUserOnline(userId);

        assertThat(result).isFalse();
    }

    @Test
    void addPendingNotification_callsRpush() throws Exception {
        UUID userId = UUID.randomUUID();
        FileChangeEvent event = new FileChangeEvent(
                UUID.randomUUID(), "/test.txt", "upload", 1, userId, Instant.now()
        );
        String expectedJson = objectMapper.writeValueAsString(event);

        dispatcher.addPendingNotification(userId, event);

        verify(listOperations).rightPush("cloud:pending:" + userId, expectedJson);
    }

    @Test
    void drainPendingNotifications_getsAllItemsAndDeletesKey() throws Exception {
        UUID userId = UUID.randomUUID();
        FileChangeEvent event = new FileChangeEvent(
                UUID.randomUUID(), "/test.txt", "upload", 1, userId, Instant.now()
        );
        String json = objectMapper.writeValueAsString(event);
        String key = "cloud:pending:" + userId;

        when(listOperations.range(key, 0, -1)).thenReturn(List.of(json));
        when(redisTemplate.delete(key)).thenReturn(true);

        List<FileChangeEvent> result = dispatcher.drainPendingNotifications(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fileId()).isEqualTo(event.fileId());
        assertThat(result.get(0).path()).isEqualTo("/test.txt");
        verify(listOperations).range(key, 0, -1);
        verify(redisTemplate).delete(key);
    }

    @Test
    void drainPendingNotifications_returnsEmptyListWhenNoItems() {
        UUID userId = UUID.randomUUID();
        String key = "cloud:pending:" + userId;

        when(listOperations.range(key, 0, -1)).thenReturn(null);
        when(redisTemplate.delete(key)).thenReturn(true);

        List<FileChangeEvent> result = dispatcher.drainPendingNotifications(userId);

        assertThat(result).isEmpty();
    }
}
