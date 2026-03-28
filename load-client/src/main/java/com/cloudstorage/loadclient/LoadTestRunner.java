package com.cloudstorage.loadclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class LoadTestRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadTestRunner.class);
    private static final UUID LOADTEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final HttpHelper http;
    private final Path videoDir;

    // Phase results
    private final List<FileUploadRecord> uploadRecords = new ArrayList<>();
    private final List<FileUploadRecord> reuploadRecords = new ArrayList<>();
    private final List<FileDownloadRecord> downloadRecords = new ArrayList<>();
    private final List<FileRevisionRecord> revisionRecords = new ArrayList<>();
    private final Map<String, Integer> baselineRevisionCounts = new HashMap<>();
    private boolean conflictTestCompleted = false;
    private String conflictTestDetails = "";

    private long uploadPhaseTotalMs = 0;
    private long reuploadPhaseTotalMs = 0;
    private long downloadPhaseTotalMs = 0;
    private long totalDataBytes = 0;

    public LoadTestRunner(HttpHelper http, Path videoDir) {
        this.http = http;
        this.videoDir = videoDir;
    }

    /**
     * Discover MP4 video files in the video directory.
     */
    public List<Path> discoverVideos() throws IOException {
        try (var stream = Files.list(videoDir)) {
            List<Path> videos = stream
                    .filter(p -> p.toString().endsWith(".mp4"))
                    .sorted()
                    .collect(Collectors.toList());
            log.info("Discovered {} video files in {}", videos.size(), videoDir);
            for (Path v : videos) {
                long size = Files.size(v);
                totalDataBytes += size;
                log.info("  {} ({} MB)", v.getFileName(), size / (1024 * 1024));
            }
            return videos;
        }
    }

    /**
     * Run all test phases and return true if overall pass.
     */
    public boolean runAllPhases(List<Path> videos) {
        boolean pass = true;

        // Snapshot existing revision counts so the test is idempotent across runs
        for (Path video : videos) {
            String filename = video.getFileName().toString();
            try {
                HttpHelper.RevisionsResult baseline = http.listRevisions(filename, 10000);
                baselineRevisionCounts.put(filename, baseline.success() ? baseline.count() : 0);
            } catch (Exception e) {
                baselineRevisionCounts.put(filename, 0);
            }
        }

        log.info("");
        log.info("=== PHASE 1: UPLOAD ===");
        pass &= runUploadPhase(videos);

        log.info("");
        log.info("=== PHASE 2: RE-UPLOAD (DELTA SYNC) ===");
        pass &= runReuploadPhase(videos);

        log.info("");
        log.info("=== PHASE 3: DOWNLOAD ===");
        pass &= runDownloadPhase(videos);

        log.info("");
        log.info("=== PHASE 4: LIST REVISIONS ===");
        pass &= runRevisionPhase(videos);

        log.info("");
        log.info("=== PHASE 5: CONCURRENT UPLOAD (CONFLICT TEST) ===");
        runConflictTest(videos);

        return pass;
    }

    // --- Phase 1: Upload ---
    private boolean runUploadPhase(List<Path> videos) {
        boolean allSuccess = true;
        long phaseStart = System.currentTimeMillis();

        for (Path video : videos) {
            String filename = video.getFileName().toString();
            try {
                HttpHelper.UploadResult result = http.upload(video, filename, LOADTEST_USER_ID, null);
                uploadRecords.add(new FileUploadRecord(filename, result));
                if (result.success()) {
                    log.info("Uploaded {}: fileId={}, version={}, blocks={}, time={}ms",
                            filename, result.fileId(), result.version(), result.blockCount(), result.timeMs());
                } else {
                    log.error("Failed to upload {}: {}", filename, result.error());
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.error("Exception uploading {}: {}", filename, e.getMessage());
                uploadRecords.add(new FileUploadRecord(filename,
                        new HttpHelper.UploadResult(false, null, 0, 0, 0, 0, e.getMessage())));
                allSuccess = false;
            }
        }

        uploadPhaseTotalMs = System.currentTimeMillis() - phaseStart;
        log.info("Upload phase completed in {}ms", uploadPhaseTotalMs);
        return allSuccess;
    }

    // --- Phase 2: Re-upload ---
    private boolean runReuploadPhase(List<Path> videos) {
        boolean allSuccess = true;
        long phaseStart = System.currentTimeMillis();

        for (Path video : videos) {
            String filename = video.getFileName().toString();
            try {
                HttpHelper.UploadResult result = http.upload(video, filename, LOADTEST_USER_ID, null);
                reuploadRecords.add(new FileUploadRecord(filename, result));
                if (result.success()) {
                    log.info("Re-uploaded {}: version={}, deduplicated={}/{}, time={}ms",
                            filename, result.version(), result.deduplicatedBlocks(),
                            result.blockCount(), result.timeMs());
                    if (result.deduplicatedBlocks() != result.blockCount()) {
                        log.warn("Expected all blocks deduplicated for {}, but got {}/{}",
                                filename, result.deduplicatedBlocks(), result.blockCount());
                    }
                } else {
                    log.error("Failed to re-upload {}: {}", filename, result.error());
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.error("Exception re-uploading {}: {}", filename, e.getMessage());
                reuploadRecords.add(new FileUploadRecord(filename,
                        new HttpHelper.UploadResult(false, null, 0, 0, 0, 0, e.getMessage())));
                allSuccess = false;
            }
        }

        reuploadPhaseTotalMs = System.currentTimeMillis() - phaseStart;
        log.info("Re-upload phase completed in {}ms", reuploadPhaseTotalMs);
        return allSuccess;
    }

    // --- Phase 3: Download ---
    private boolean runDownloadPhase(List<Path> videos) {
        boolean allSuccess = true;
        long phaseStart = System.currentTimeMillis();

        for (Path video : videos) {
            String filename = video.getFileName().toString();
            try {
                long expectedSize = Files.size(video);
                HttpHelper.DownloadResult result = http.download(filename);
                boolean sizeMatch = result.sizeBytes() == expectedSize;
                downloadRecords.add(new FileDownloadRecord(filename, result, expectedSize, sizeMatch));

                if (result.success()) {
                    log.info("Downloaded {}: size={} bytes, expected={}, match={}, time={}ms",
                            filename, result.sizeBytes(), expectedSize, sizeMatch, result.timeMs());
                    if (!sizeMatch) {
                        allSuccess = false;
                    }
                } else {
                    log.error("Failed to download {}: {}", filename, result.error());
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.error("Exception downloading {}: {}", filename, e.getMessage());
                downloadRecords.add(new FileDownloadRecord(filename,
                        new HttpHelper.DownloadResult(false, 0, 0, e.getMessage()), 0, false));
                allSuccess = false;
            }
        }

        downloadPhaseTotalMs = System.currentTimeMillis() - phaseStart;
        log.info("Download phase completed in {}ms", downloadPhaseTotalMs);
        return allSuccess;
    }

    // --- Phase 4: Revisions ---
    private boolean runRevisionPhase(List<Path> videos) {
        boolean allSuccess = true;

        for (Path video : videos) {
            String filename = video.getFileName().toString();
            try {
                HttpHelper.RevisionsResult result = http.listRevisions(filename, 10000);
                revisionRecords.add(new FileRevisionRecord(filename, result));

                if (result.success()) {
                    String versionsStr = Arrays.toString(result.versions());
                    log.info("Revisions for {}: count={}, versions={}", filename, result.count(), versionsStr);
                    int baseline = baselineRevisionCounts.getOrDefault(filename, 0);
                    int expected = baseline + 2;
                    if (result.count() != expected) {
                        log.warn("Expected {} revisions for {} (baseline {}+2), got {}",
                                expected, filename, baseline, result.count());
                        allSuccess = false;
                    }
                } else {
                    log.error("Failed to list revisions for {}: {}", filename, result.error());
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.error("Exception listing revisions for {}: {}", filename, e.getMessage());
                revisionRecords.add(new FileRevisionRecord(filename,
                        new HttpHelper.RevisionsResult(false, 0, null, e.getMessage())));
                allSuccess = false;
            }
        }

        return allSuccess;
    }

    // --- Phase 5: Conflict test ---
    private void runConflictTest(List<Path> videos) {
        if (videos.isEmpty()) {
            log.warn("No videos available for conflict test");
            conflictTestDetails = "No videos available";
            return;
        }

        Path video = videos.get(0);
        String filename = video.getFileName().toString();
        log.info("Running concurrent upload conflict test with {}", filename);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpHelper.UploadResult> future1 = executor.submit(() ->
                    http.upload(video, filename, LOADTEST_USER_ID, 2));
            Future<HttpHelper.UploadResult> future2 = executor.submit(() ->
                    http.upload(video, filename, LOADTEST_USER_ID, 2));

            HttpHelper.UploadResult result1 = future1.get();
            HttpHelper.UploadResult result2 = future2.get();

            log.info("Concurrent upload 1: success={}, version={}, error={}",
                    result1.success(), result1.version(), result1.error());
            log.info("Concurrent upload 2: success={}, version={}, error={}",
                    result2.success(), result2.version(), result2.error());

            conflictTestCompleted = true;
            conflictTestDetails = String.format("Thread 1: success=%s version=%d, Thread 2: success=%s version=%d",
                    result1.success(), result1.version(), result2.success(), result2.version());
        } catch (Exception e) {
            log.error("Conflict test failed: {}", e.getMessage());
            conflictTestDetails = "Exception: " + e.getMessage();
        } finally {
            executor.shutdown();
        }
    }

    // --- Phase 6: Report ---
    public void printReport(int fileCount, boolean overallPass) {
        double totalDataMB = totalDataBytes / (1024.0 * 1024.0);
        double uploadTimeSec = uploadPhaseTotalMs / 1000.0;
        double reuploadTimeSec = reuploadPhaseTotalMs / 1000.0;
        double downloadTimeSec = downloadPhaseTotalMs / 1000.0;

        double uploadThroughput = uploadTimeSec > 0 ? totalDataMB / uploadTimeSec : 0;
        double downloadThroughput = downloadTimeSec > 0 ? totalDataMB / downloadTimeSec : 0;
        double speedup = uploadPhaseTotalMs > 0 ? (double) uploadPhaseTotalMs / reuploadPhaseTotalMs : 0;

        boolean allUploadsOk = uploadRecords.stream().allMatch(r -> r.result().success());
        boolean allDedup = reuploadRecords.stream().allMatch(r ->
                r.result().success() && r.result().deduplicatedBlocks() == r.result().blockCount());
        int dedupPercent = allDedup ? 100 : calculateDedupPercent();
        boolean allSizesMatch = downloadRecords.stream().allMatch(FileDownloadRecord::sizeMatch);
        boolean allRevisions = revisionRecords.stream().allMatch(r ->
                r.result().success() && r.result().count() ==
                        baselineRevisionCounts.getOrDefault(r.filename(), 0) + 2);

        System.out.println();
        System.out.println("========================================");
        System.out.println("  CLOUD STORAGE LOAD TEST REPORT");
        System.out.println("========================================");
        System.out.printf("Files tested: %d%n", fileCount);
        System.out.printf("Total data: %.0f MB%n", totalDataMB);
        System.out.println();

        System.out.println("UPLOAD PHASE:");
        System.out.printf("  Total time: %.1fs%n", uploadTimeSec);
        System.out.printf("  Throughput: %.1f MB/s%n", uploadThroughput);
        System.out.printf("  All uploads successful: %s%n", allUploadsOk ? "YES" : "NO");
        System.out.println();

        System.out.println("RE-UPLOAD PHASE (DELTA SYNC):");
        System.out.printf("  Total time: %.1fs%n", reuploadTimeSec);
        System.out.printf("  Deduplication rate: %d%%%n", dedupPercent);
        System.out.printf("  Speedup vs initial upload: %.1fx%n", speedup);
        System.out.println();

        System.out.println("DOWNLOAD PHASE:");
        System.out.printf("  Total time: %.1fs%n", downloadTimeSec);
        System.out.printf("  Throughput: %.1f MB/s%n", downloadThroughput);
        System.out.printf("  All sizes match: %s%n", allSizesMatch ? "YES" : "NO");
        System.out.println();

        System.out.println("REVISION PHASE:");
        System.out.printf("  All files have 2 revisions: %s%n", allRevisions ? "YES" : "NO");
        System.out.println();

        System.out.println("CONFLICT TEST:");
        System.out.printf("  Concurrent uploads completed: %s%n", conflictTestCompleted ? "YES" : "NO");
        System.out.println();

        System.out.printf("OVERALL: %s%n", overallPass ? "PASS" : "FAIL");
        System.out.println("========================================");
    }

    private int calculateDedupPercent() {
        int totalBlocks = 0;
        int dedupBlocks = 0;
        for (FileUploadRecord r : reuploadRecords) {
            if (r.result().success()) {
                totalBlocks += r.result().blockCount();
                dedupBlocks += r.result().deduplicatedBlocks();
            }
        }
        return totalBlocks > 0 ? (int) ((dedupBlocks * 100L) / totalBlocks) : 0;
    }

    // --- Record types ---

    record FileUploadRecord(String filename, HttpHelper.UploadResult result) {}

    record FileDownloadRecord(String filename, HttpHelper.DownloadResult result,
                              long expectedSize, boolean sizeMatch) {}

    record FileRevisionRecord(String filename, HttpHelper.RevisionsResult result) {}
}
