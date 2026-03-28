package com.cloudstorage.apiserver.controller;

import com.cloudstorage.apiserver.service.ColdStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final ColdStorageService coldStorageService;

    @PostMapping("/cold-storage/trigger")
    public ResponseEntity<Void> triggerColdStorage(
            @RequestParam(value = "days", defaultValue = "0") int days) {
        coldStorageService.moveBlocksOlderThan(days);
        return ResponseEntity.noContent().build();
    }
}
