package com.cloudstorage.loadclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

public class HttpHelper {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String targetHost;

    public HttpHelper(String targetHost) {
        this.targetHost = targetHost;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Poll the health endpoint until the system is ready.
     * Retries up to maxRetries times with the given delay between attempts.
     */
    public boolean waitForReady(int maxRetries, Duration delay) {
        for (int i = 1; i <= maxRetries; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(targetHost + "/health"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return true;
                }
            } catch (Exception e) {
                // ignore and retry
            }
            if (i < maxRetries) {
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Upload a file via multipart/form-data POST to /files/upload.
     * Manually constructs the multipart body with boundary.
     */
    public UploadResult upload(Path filePath, String filename, UUID userId, Integer expectedVersion)
            throws IOException, InterruptedException {

        String boundary = "----LoadTestBoundary" + System.nanoTime();
        byte[] body = buildMultipartBody(boundary, filePath, filename, userId, expectedVersion);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetHost + "/files/upload"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new UploadResult(false, null, 0, 0, 0, elapsed,
                    "HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        String fileId = json.get("fileId").asText();
        int version = json.get("version").asInt();
        int blockCount = json.get("blockCount").asInt();
        int deduplicatedBlocks = json.get("deduplicatedBlocks").asInt();

        return new UploadResult(true, fileId, version, blockCount, deduplicatedBlocks, elapsed, null);
    }

    /**
     * Download a file via GET /files/download?path=/{filename}.
     * Returns the raw bytes and timing information.
     */
    public DownloadResult download(String filename) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetHost + "/files/download?path=/" + filename))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        long elapsed = System.currentTimeMillis() - start;

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new DownloadResult(false, 0, elapsed,
                    "HTTP " + response.statusCode());
        }

        return new DownloadResult(true, response.body().length, elapsed, null);
    }

    /**
     * List revisions via GET /files/list_revisions?path=/{filename}&limit={limit}.
     */
    public RevisionsResult listRevisions(String filename, int limit) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetHost + "/files/list_revisions?path=/" + filename + "&limit=" + limit))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new RevisionsResult(false, 0, null,
                    "HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        int count = json.size();
        int[] versions = new int[count];
        for (int i = 0; i < count; i++) {
            versions[i] = json.get(i).get("versionNumber").asInt();
        }

        return new RevisionsResult(true, count, versions, null);
    }

    private byte[] buildMultipartBody(String boundary, Path filePath, String filename,
                                      UUID userId, Integer expectedVersion) throws IOException {

        byte[] fileBytes = Files.readAllBytes(filePath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String crlf = "\r\n";
        String boundaryLine = "--" + boundary;

        // Part: file
        baos.write((boundaryLine + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"" + crlf)
                .getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Type: application/octet-stream" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        baos.write(fileBytes);
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));

        // Part: filename
        baos.write((boundaryLine + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"filename\"" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        baos.write(filename.getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));

        // Part: userId
        baos.write((boundaryLine + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(("Content-Disposition: form-data; name=\"userId\"" + crlf).getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        baos.write(userId.toString().getBytes(StandardCharsets.UTF_8));
        baos.write(crlf.getBytes(StandardCharsets.UTF_8));

        // Part: expectedVersion (optional)
        if (expectedVersion != null) {
            baos.write((boundaryLine + crlf).getBytes(StandardCharsets.UTF_8));
            baos.write(("Content-Disposition: form-data; name=\"expectedVersion\"" + crlf)
                    .getBytes(StandardCharsets.UTF_8));
            baos.write(crlf.getBytes(StandardCharsets.UTF_8));
            baos.write(String.valueOf(expectedVersion).getBytes(StandardCharsets.UTF_8));
            baos.write(crlf.getBytes(StandardCharsets.UTF_8));
        }

        // Closing boundary
        baos.write((boundaryLine + "--" + crlf).getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }

    /**
     * Query a Prometheus counter total via the Prometheus HTTP API.
     * Uses sum() to aggregate across all instances.
     */
    public double queryMetricTotal(String metricName) throws IOException, InterruptedException {
        String query = "sum(" + metricName + ")";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetHost + "/prometheus/api/v1/query?query=" + query))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return -1;
        }

        JsonNode json = objectMapper.readTree(response.body());
        JsonNode result = json.path("data").path("result");
        if (result.isEmpty()) {
            return 0;
        }
        return result.get(0).path("value").get(1).asDouble();
    }

    /**
     * Trigger cold storage migration via POST /internal/cold-storage/trigger?days=0.
     */
    public boolean triggerColdStorage(int days) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetHost + "/internal/cold-storage/trigger?days=" + days))
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /**
     * List conflicts via GET /files/conflicts?userId={userId}.
     */
    public ConflictsResult listConflicts(UUID userId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetHost + "/files/conflicts?userId=" + userId))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return new ConflictsResult(false, 0, null,
                    "HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode json = objectMapper.readTree(response.body());
        int count = json.size();
        String[] ids = new String[count];
        for (int i = 0; i < count; i++) {
            ids[i] = json.get(i).get("id").asText();
        }
        return new ConflictsResult(true, count, ids, null);
    }

    /**
     * Resolve a conflict via POST /files/conflicts/{id}/resolve?resolution={resolution}.
     */
    public boolean resolveConflict(String conflictId, String resolution)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetHost + "/files/conflicts/" + conflictId
                        + "/resolve?resolution=" + resolution))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    // --- Result records ---

    public record UploadResult(
            boolean success,
            String fileId,
            int version,
            int blockCount,
            int deduplicatedBlocks,
            long timeMs,
            String error
    ) {}

    public record DownloadResult(
            boolean success,
            long sizeBytes,
            long timeMs,
            String error
    ) {}

    public record RevisionsResult(
            boolean success,
            int count,
            int[] versions,
            String error
    ) {}

    public record ConflictsResult(
            boolean success,
            int count,
            String[] ids,
            String error
    ) {}
}
