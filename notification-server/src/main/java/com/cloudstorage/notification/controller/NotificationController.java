package com.cloudstorage.notification.controller;

import com.cloudstorage.common.event.FileChangeEvent;
import com.cloudstorage.notification.service.NotificationDispatcher;
import com.cloudstorage.notification.service.OfflineQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationDispatcher dispatcher;
    private final OfflineQueueService offlineQueueService;

    @PostMapping("/online")
    public ResponseEntity<Void> markOnline(@RequestParam("userId") UUID userId) {
        dispatcher.markUserOnline(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/offline")
    public ResponseEntity<Void> markOffline(@RequestParam("userId") UUID userId) {
        dispatcher.markUserOffline(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending")
    public ResponseEntity<List<FileChangeEvent>> getPending(@RequestParam("userId") UUID userId) {
        List<FileChangeEvent> pending = dispatcher.drainPendingNotifications(userId);
        List<FileChangeEvent> offline = offlineQueueService.drainOfflineQueue(userId);

        List<FileChangeEvent> combined = new ArrayList<>(pending);
        combined.addAll(offline);

        dispatcher.markUserOnline(userId);

        log.info("Returning {} pending notifications for user {}", combined.size(), userId);
        return ResponseEntity.ok(combined);
    }
}
