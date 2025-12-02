package dev.coms4156.project.metadetect.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Loads logistic regression model parameters (weights + bias) from a JSON file.
 * The location is configurable via {@code metadetect.model.path} and defaults
 * to {@code classpath:model/model.json}. The loader caches the parsed model to
 * avoid repeated disk I/O.
 */
@Component
public class ModelLoader {

  private static final Logger log = LoggerFactory.getLogger(ModelLoader.class);

  private final ResourceLoader resourceLoader;
  private final ObjectMapper objectMapper;
  private final String modelLocation;
  private volatile ModelParameters cached;

  /**
   * Constructs a loader for logistic regression model parameters.
   *
   * @param resourceLoader resource resolver used to locate the model.json file
   * @param objectMapper JSON mapper for parsing the weights/bias file
   * @param modelLocation configurable location (e.g., classpath:model/model.json)
   */
  public ModelLoader(ResourceLoader resourceLoader,
                     ObjectMapper objectMapper,
                     @Value("${main.resources.path:classpath:/model.json}")
                     String modelLocation) {
    this.resourceLoader = resourceLoader;
    this.objectMapper = objectMapper;
    this.modelLocation = modelLocation;
  }

  /**
   * Loads the configured model, caching the result for subsequent calls.
   *
   * @return immutable model parameters
   */
  public ModelParameters loadModel() {
    if (cached != null) {
      return cached;
    }

    synchronized (this) {
      if (cached == null) {
        cached = readModel();
      }
      return cached;
    }
  }

  private ModelParameters readModel() {
    Resource resource = resolve(modelLocation);
    if (!resource.exists()) {
      throw new IllegalStateException("Model file not found at " + modelLocation);
    }

    try (InputStream is = resource.getInputStream()) {
      ModelJson raw = objectMapper.readValue(is, ModelJson.class);
      if (raw.weights == null || raw.weights.length == 0) {
        throw new IllegalStateException("Model weights are missing or empty");
      }
      if (Arrays.stream(raw.weights).anyMatch(d -> Double.isNaN(d) || Double.isInfinite(d))) {
        throw new IllegalStateException("Model weights contain invalid values");
      }
      double bias = Double.isNaN(raw.bias) || Double.isInfinite(raw.bias) ? 0.0 : raw.bias;
      String version = raw.version == null || raw.version.isBlank() ? "v1" : raw.version;
      log.info("Loaded logistic regression model: {} ({} weights)", version, raw.weights.length);
      return new ModelParameters(raw.weights, bias, version);
    } catch (IOException ioe) {
      throw new IllegalStateException("Failed to read model from " + modelLocation, ioe);
    }
  }

  private Resource resolve(String location) {
    if (location.startsWith("classpath:") || location.startsWith("file:")) {
      return resourceLoader.getResource(location);
    }
    // Default to file: for bare paths
    return resourceLoader.getResource("file:" + location);
  }

  /** Immutable holder for model parameters. */
  public record ModelParameters(double[] weights, double bias, String version) { }

  /** Internal mapping of the JSON schema. */
  private static final class ModelJson {
    public double[] weights;
    public double bias;
    public String version;
  }
}
