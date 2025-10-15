package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.*;
import dev.coms4156.project.metadetect.service.AnalyzeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

/** Endpoints for analysis, metadata, confidence, compare. */
@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final AnalyzeService analyzeService;

    public AnalyzeController(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    @PostMapping(value = "/analyze", consumes = {"multipart/form-data"})
    public ResponseEntity<AnalyzeResponse> analyze(
            @RequestPart("image") MultipartFile image,
            @RequestPart(value = "options", required = false) AnalyzeOptions options) {
        // TODO: validate mime/size; persist; enqueue/compute; return id + status
        AnalyzeResponse stub = new AnalyzeResponse("stub-id", 0.42, "PENDING", Instant.now(), null);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(stub);
    }

    @GetMapping("/metadata/{id}")
    public ResponseEntity<MetadataResponse> metadata(@PathVariable String id) {
        // TODO: fetch parsed EXIF/metadata from DB
        return ResponseEntity.ok(new MetadataResponse(id));
    }

    @GetMapping("/confidence/{id}")
    public ResponseEntity<ConfidenceResponse> confidence(@PathVariable String id) {
        // TODO: fetch score + status from DB
        return ResponseEntity.ok(new ConfidenceResponse(id, 0.42, "PENDING"));
    }

    @PostMapping(value = "/compare", consumes = {"application/json", "multipart/form-data"})
    public ResponseEntity<CompareResponse> compare(
            @RequestBody(required = false) CompareRequest byIds,
            @RequestPart(value = "imageA", required = false) MultipartFile imageA,
            @RequestPart(value = "imageB", required = false) MultipartFile imageB) {
        // TODO: support id mode and file mode; compute similarity
        return ResponseEntity.ok(new CompareResponse("a", "b", 0.13));
    }
}
