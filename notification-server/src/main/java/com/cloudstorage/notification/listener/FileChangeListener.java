package com.cloudstorage.notification.listener;

import com.cloudstorage.common.event.FileChangeEvent;
import com.cloudstorage.notification.service.NotificationDispatcher;
import com.cloudstorage.notification.service.OfflineQueueService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FileChangeListener {

    private final NotificationDispatcher notificationDispatcher;
    private final OfflineQueueService offlineQueueService;
    private final Counter notificationsReceivedCounter;

    public FileChangeListener(NotificationDispatcher notificationDispatcher,
                              OfflineQueueService offlineQueueService,
                              MeterRegistry meterRegistry) {
        this.notificationDispatcher = notificationDispatcher;
        this.offlineQueueService = offlineQueueService;
        this.notificationsReceivedCounter = Counter.builder("cloud_storage_notifications_received_total")
                .description("Total number of file change notifications received")
                .register(meterRegistry);
    }

    @RabbitListener(queues = "file-change-notifications")
    public void handleFileChange(FileChangeEvent event) {
        log.info("Received file change event: fileId={}, eventType={}, userId={}",
                event.fileId(), event.eventType(), event.userId());

        notificationsReceivedCounter.increment();

        if (notificationDispatcher.isUserOnline(event.userId())) {
            notificationDispatcher.addPendingNotification(event.userId(), event);
            log.debug("User {} is online, added to pending notifications", event.userId());
        } else {
            offlineQueueService.queueForOfflineUser(event.userId(), event);
            log.debug("User {} is offline, queued in offline backup", event.userId());
        }
    }
}
