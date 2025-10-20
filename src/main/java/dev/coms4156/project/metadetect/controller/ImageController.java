package dev.coms4156.project.metadetect.controller;

import dev.coms4156.project.metadetect.dto.Dtos;
import dev.coms4156.project.metadetect.service.ImageService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD around user images (records, not binary).
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

  private final ImageService imageService;

  public ImageController(ImageService imageService) {
    this.imageService = imageService;
  }

  @GetMapping
  public ResponseEntity<List<Dtos.ImageDto>> list(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
    // TODO: paginate by owner user
    return ResponseEntity.ok(List.of());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> get(@PathVariable String id) {
    // TODO: fetch by id + ownership
    return ResponseEntity.ok(new Dtos.ImageDto(id, "original.jpg", "owner-uid", null));
  }

  @PutMapping("/{id}")
  public ResponseEntity<Dtos.ImageDto> update(@PathVariable String id,
                                              @RequestBody Dtos.UpdateImageRequest req) {
    // TODO: update mutable fields (labels, note)
    return ResponseEntity.ok(new Dtos.ImageDto(id, "original.jpg", "owner-uid", null));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    // TODO: soft/hard delete image + reports
    return ResponseEntity.noContent().build();
  }
}
