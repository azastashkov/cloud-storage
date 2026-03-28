package com.cloudstorage.notification.listener;

import com.cloudstorage.common.event.FileChangeEvent;
import com.cloudstorage.notification.service.NotificationDispatcher;
import com.cloudstorage.notification.service.OfflineQueueService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileChangeListenerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private OfflineQueueService offlineQueueService;

    private MeterRegistry meterRegistry;
    private FileChangeListener listener;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        listener = new FileChangeListener(notificationDispatcher, offlineQueueService, meterRegistry);
    }

    @Test
    void handleFileChange_userOnline_addsToDispatcher() {
        UUID userId = UUID.randomUUID();
        FileChangeEvent event = new FileChangeEvent(
                UUID.randomUUID(), "/docs/test.txt", "upload", 1, userId, Instant.now()
        );

        when(notificationDispatcher.isUserOnline(userId)).thenReturn(true);

        listener.handleFileChange(event);

        verify(notificationDispatcher).addPendingNotification(userId, event);
        verifyNoInteractions(offlineQueueService);
    }

    @Test
    void handleFileChange_userOffline_queuesInOfflineService() {
        UUID userId = UUID.randomUUID();
        FileChangeEvent event = new FileChangeEvent(
                UUID.randomUUID(), "/docs/test.txt", "upload", 1, userId, Instant.now()
        );

        when(notificationDispatcher.isUserOnline(userId)).thenReturn(false);

        listener.handleFileChange(event);

        verify(offlineQueueService).queueForOfflineUser(userId, event);
        verify(notificationDispatcher).isUserOnline(userId);
    }
}
