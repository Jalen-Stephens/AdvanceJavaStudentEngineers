package dev.coms4156.project.metadetect.c2pa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Invokes the C2PA command-line tool to extract ML-friendly metadata from images. */
public class C2paToolInvoker {

  private static final Logger log = LoggerFactory.getLogger(C2paToolInvoker.class);

  /**
   * Keywords used to flag AI generators in the claim_generator field.
   * This list is intentionally conservative and can be refined in future iterations.
   */
  private static final List<String> AI_GENERATOR_KEYWORDS = Arrays.asList(
      "firefly",
      "dall-e",
      "dalle",
      "midjourney",
      "stable diffusion",
      "stability",
      "sdxl",
      "gpt",
      "openai",
      "bing image creator",
      "copilot",
      "canva",
      "runway"
  );

  private final String c2paToolPath;

  /**
   * Kept for backwards compatibility / reuse in logs; no longer used for exceptions.
   */
  public static final String NO_C2PA_MANIFEST_MESSAGE =
      "This image does not contain C2PA data (no C2PA manifest found).";

  public C2paToolInvoker(String c2paToolPath) {
    this.c2paToolPath = c2paToolPath;
  }

  /**
   * Executes the C2PA tool and converts its output into ML-ready metadata.
   *
   * <p>Contract:
   * <ul>
   *   <li>Never throws to callers.</li>
   *   <li>Always returns a fully populated {@link C2paMetadata} instance.</li>
   *   <li>"No claim found" is treated as a soft success (no manifest, no error).</li>
   *   <li>Unexpected CLI / JSON errors set {@code c2pa_errorFlag = 1}.</li>
   * </ul>
   */
  public C2paMetadata extractMetadata(File imageFile) {
    if (imageFile == null) {
      String msg = "imageFile is null";
      log.warn(msg);
      return C2paMetadata.error(msg);
    }

    try {
      ProcessBuilder pb = new ProcessBuilder(
          c2paToolPath,
          imageFile.getAbsolutePath(),
          "-d" // detailed JSON output
      );
      pb.redirectErrorStream(false);

      Process proc = pb.start();
      try (InputStream out = proc.getInputStream();
           InputStream err = proc.getErrorStream();
           Scanner so = new Scanner(out, StandardCharsets.UTF_8);
           Scanner se = new Scanner(err, StandardCharsets.UTF_8)) {

        int exit = proc.waitFor();
        String stdout = so.useDelimiter("\\A").hasNext() ? so.next() : "";
        String stderr = se.useDelimiter("\\A").hasNext() ? se.next() : "";

        if (exit != 0) {
          String lowerStderr = stderr == null ? "" : stderr.toLowerCase();

          // Soft failure: no manifest present
          if (!lowerStderr.isBlank() && lowerStderr.contains("no claim found")) {
            log.debug("c2patool reported no claim found for file {}: {}",
                imageFile.getName(), stderr);
            return C2paMetadata.noManifest();
          }

          // Hard CLI failure: record as error for ML layer
          String msg = "C2PA tool failed with exit code " + exit
              + (stderr == null || stderr.isBlank() ? "" : " | stderr: " + stderr.trim());
          log.warn(msg);
          return C2paMetadata.error(msg);
        }

        if (stdout == null || stdout.isBlank()) {
          String msg = "c2patool returned empty output for file " + imageFile.getName();
          log.warn(msg);
          return C2paMetadata.error(msg);
        }

        // Raw JSON is still easy to inspect locally via logs.
        log.debug("c2patool output for {}: {}", imageFile.getName(), stdout);
        return parseMetadataFromJson(stdout);

      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        String msg = "C2PA tool execution was interrupted: " + ie;
        log.warn(msg);
        return C2paMetadata.error(msg);
      }
    } catch (Exception e) {
      String msg = "Unexpected error while invoking C2PA tool: " + e;
      log.warn(msg, e);
      return C2paMetadata.error(msg);
    }
  }

  /**
   * Parses the JSON output from {@code c2patool -d} into the ML metadata schema.
   *
   * <p>On any JSON/shape error, this returns a metadata object with errorFlag=1.
   */
  private C2paMetadata parseMetadataFromJson(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(json);
      if (root == null || root.isMissingNode()) {
        String msg = "Unable to parse c2patool JSON output (root is missing)";
        log.warn(msg);
        return C2paMetadata.error(msg);
      }

      int manifestCount = 0;
      int hasManifest = 0;
      String claimGenerator = null;
      int claimGeneratorIsAI = 0;

      // Count manifests
      JsonNode manifestsNode = root.get("manifests");
      if (manifestsNode != null && manifestsNode.isObject()) {
        manifestCount = manifestsNode.size();
      }

      if (manifestCount > 0) {
        hasManifest = 1;

        // Resolve active manifest (preferred) or fall back to first manifest.
        JsonNode activeManifestField = root.get("active_manifest");
        String activeManifestId =
            (activeManifestField != null && !activeManifestField.isNull())
                ? activeManifestField.asText(null)
                : null;

        JsonNode activeManifestNode = null;
        if (activeManifestId != null && manifestsNode != null && manifestsNode.isObject()) {
          activeManifestNode = manifestsNode.get(activeManifestId);
        }
        if (activeManifestNode == null && manifestsNode != null && manifestsNode.isObject()) {
          var it = manifestsNode.elements();
          if (it.hasNext()) {
            activeManifestNode = it.next();
          }
        }

        if (activeManifestNode != null && !activeManifestNode.isMissingNode()) {
          JsonNode claimNode = activeManifestNode.get("claim");
          if (claimNode == null || claimNode.isMissingNode()) {
            claimNode = activeManifestNode;
          }
          // 1) New standard location: claim.claim_generator_info.name
          JsonNode genInfo = claimNode.get("claim_generator_info");
          if (genInfo != null && genInfo.has("name")) {
              claimGenerator = genInfo.get("name").asText(null);
          }
          // 2) Legacy location: claim.claim_generator
          if (claimGenerator == null) {
              JsonNode cgLegacy = claimNode.get("claim_generator");
              if (cgLegacy != null && !cgLegacy.isNull()) {
                  claimGenerator = cgLegacy.asText(null);
              }
          }
        }
      } else {
        hasManifest = 0;
      }

      if (claimGenerator != null && !claimGenerator.isBlank()
          && isAiClaimGenerator(claimGenerator)) {
        claimGeneratorIsAI = 1;
      } else {
        claimGeneratorIsAI = 0;
      }

      return new C2paMetadata(
          hasManifest,
          manifestCount,
          claimGenerator,
          claimGeneratorIsAI,
          /*c2pa_errorFlag*/ 0,
          /*c2pa_errorMessage*/ null
      );
    } catch (Exception e) {
      String msg = "Failed to parse c2patool JSON output: " + e;
      log.warn(msg, e);
      return C2paMetadata.error(msg);
    }
  }

  private static boolean isAiClaimGenerator(String generator) {
    if (generator == null) {
      return false;
    }
    String lower = generator.toLowerCase();
    for (String kw : AI_GENERATOR_KEYWORDS) {
      if (lower.contains(kw)) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------------
  // ML-ready metadata schema for future model integration.
  // ---------------------------------------------------------------------------

  @JsonInclude(JsonInclude.Include.ALWAYS)
  @JsonPropertyOrder({
      "c2pa_hasManifest",
      "c2pa_manifestCount",
      "c2pa_claimGenerator",
      "c2pa_claimGeneratorIsAI",
      "c2pa_errorFlag",
      "c2pa_errorMessage"
  })
  public static final class C2paMetadata {

    private final int c2pa_hasManifest;
    private final int c2pa_manifestCount;
    private final String c2pa_claimGenerator;
    private final int c2pa_claimGeneratorIsAI;
    private final int c2pa_errorFlag;
    private final String c2pa_errorMessage;

    public C2paMetadata(
        int c2pa_hasManifest,
        int c2pa_manifestCount,
        String c2pa_claimGenerator,
        int c2pa_claimGeneratorIsAI,
        int c2pa_errorFlag,
        String c2pa_errorMessage) {

      this.c2pa_hasManifest = c2pa_hasManifest;
      this.c2pa_manifestCount = c2pa_manifestCount;
      this.c2pa_claimGenerator = c2pa_claimGenerator;
      this.c2pa_claimGeneratorIsAI = c2pa_claimGeneratorIsAI;
      this.c2pa_errorFlag = c2pa_errorFlag;
      this.c2pa_errorMessage = c2pa_errorMessage;
    }

    /** Factory for the “no manifest” soft-success case. */
    public static C2paMetadata noManifest() {
      return new C2paMetadata(
          /*c2pa_hasManifest*/ 0,
          /*c2pa_manifestCount*/ 0,
          /*c2pa_claimGenerator*/ null,
          /*c2pa_claimGeneratorIsAI*/ 0,
          /*c2pa_errorFlag*/ 0,
          /*c2pa_errorMessage*/ null
      );
    }

    /** Factory for hard failures (CLI / JSON / unexpected). */
    public static C2paMetadata error(String message) {
      return new C2paMetadata(
          /*c2pa_hasManifest*/ 0,
          /*c2pa_manifestCount*/ 0,
          /*c2pa_claimGenerator*/ null,
          /*c2pa_claimGeneratorIsAI*/ 0,
          /*c2pa_errorFlag*/ 1,
          /*c2pa_errorMessage*/ message
      );
    }

    public int getC2pa_hasManifest() {
      return c2pa_hasManifest;
    }

    public int getC2pa_manifestCount() {
      return c2pa_manifestCount;
    }

    public String getC2pa_claimGenerator() {
      return c2pa_claimGenerator;
    }

    public int getC2pa_claimGeneratorIsAI() {
      return c2pa_claimGeneratorIsAI;
    }

    public int getC2pa_errorFlag() {
      return c2pa_errorFlag;
    }

    public String getC2pa_errorMessage() {
      return c2pa_errorMessage;
    }
  }
}