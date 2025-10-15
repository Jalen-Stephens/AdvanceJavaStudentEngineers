package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.ImageDto;
import dev.coms4156.project.metadetect.dto.UpdateImageRequest;
import dev.coms4156.project.metadetect.service.ImageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD around user images (records, not binary). */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) { this.imageService = imageService; }

    @GetMapping
    public ResponseEntity<List<ImageDto>> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        // TODO: paginate by owner user
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImageDto> get(@PathVariable String id) {
        // TODO: fetch by id + ownership
        return ResponseEntity.ok(new ImageDto(id, "original.jpg", "owner-uid", null));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ImageDto> update(@PathVariable String id,
                                           @RequestBody UpdateImageRequest req) {
        // TODO: update mutable fields (labels, note)
        return ResponseEntity.ok(new ImageDto(id, "original.jpg", "owner-uid", null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        // TODO: soft/hard delete image + reports
        return ResponseEntity.noContent().build();
    }
}
