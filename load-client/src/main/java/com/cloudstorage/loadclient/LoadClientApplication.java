package com.cloudstorage.loadclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

@SpringBootApplication
public class LoadClientApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadClientApplication.class);

    @Value("${TARGET_HOST:http://localhost}")
    private String targetHost;

    @Value("${VIDEO_DIR:/videos}")
    private String videoDir;

    public static void main(String[] args) {
        SpringApplication.run(LoadClientApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("Cloud Storage Load Test Client starting...");
        log.info("Target host: {}", targetHost);
        log.info("Video directory: {}", videoDir);

        HttpHelper http = new HttpHelper(targetHost);

        // Step 1: Wait for system to be ready
        log.info("Waiting for system to be ready...");
        boolean ready = http.waitForReady(60, Duration.ofSeconds(5));
        if (!ready) {
            log.error("System did not become ready after 60 retries. Exiting.");
            System.exit(1);
            return;
        }
        log.info("System is ready!");

        try {
            Path videoDirPath = Paths.get(videoDir);
            LoadTestRunner runner = new LoadTestRunner(http, videoDirPath);

            // Step 2: Discover video files
            List<Path> videos = runner.discoverVideos();
            if (videos.isEmpty()) {
                log.error("No video files found in {}. Exiting.", videoDir);
                System.exit(1);
                return;
            }

            // Step 3: Run test phases
            boolean pass = runner.runAllPhases(videos);

            // Step 4: Print report
            runner.printReport(videos.size(), pass);

            // Step 5: Exit
            System.exit(pass ? 0 : 1);

        } catch (Exception e) {
            log.error("Load test failed with exception", e);
            System.exit(1);
        }
    }
}
