package dev.coms4156.project.metadetect.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Coordinates metadata, forensic, and model steps. */
@Service
public class AnalyzeService {
    // TODO: inject repositories + compute engines when added

    public String submitAnalysis(MultipartFile file) {
        // TODO: validate/normalize; persist; enqueue; return id
        return "stub-id";
    }

    // TODO: methods to fetch metadata, confidence, compare, etc.
}
