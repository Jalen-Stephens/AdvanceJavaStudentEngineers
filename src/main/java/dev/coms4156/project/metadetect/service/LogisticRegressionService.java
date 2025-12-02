package dev.coms4156.project.metadetect.service;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker.C2paMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Performs logistic regression inference against the feature vector produced by
 * {@link FeatureExtractor}. The model weights and bias are loaded once from JSON via
 * {@link ModelLoader}.
 */
@Service
public class LogisticRegressionService {

  private static final Logger log = LoggerFactory.getLogger(LogisticRegressionService.class);

  private final FeatureExtractor featureExtractor;
  private final ModelLoader modelLoader;

  public LogisticRegressionService(FeatureExtractor featureExtractor, ModelLoader modelLoader) {
    this.featureExtractor = featureExtractor;
    this.modelLoader = modelLoader;
  }

  /**
   * Generates an AI confidence score for the given image.
   *
   * @param imagePath path to the downloaded image on disk
   * @param c2pa pre-extracted C2PA metadata (never null in current pipeline)
   * @return inference result containing the probability, c2pa usage flag, and model version
   */
  public InferenceResult predict(String imagePath, C2paMetadata c2pa) {
    ModelLoader.ModelParameters model = modelLoader.loadModel();
    double[] features = featureExtractor.extractAllFeatures(imagePath, c2pa);
    double z = dot(model.weights(), features) + model.bias();
    double probability = sigmoid(z);
    boolean c2paUsed = c2pa != null
        && c2pa.getc2paHasManifest() == 1
        && c2pa.getc2paErrorFlag() == 0;

    return new InferenceResult(probability, c2paUsed, model.version());
  }

  /** Returns the loaded model version to surface in responses. */
  public String getModelVersion() {
    return modelLoader.loadModel().version();
  }

  private double dot(double[] weights, double[] features) {
    int len = Math.min(weights.length, features.length);
    if (weights.length != features.length) {
      log.warn("Model/feature length mismatch (w={}, f={}); truncating to {}", weights.length,
          features.length, len);
    }

    double sum = 0.0;
    for (int i = 0; i < len; i++) {
      sum += weights[i] * features[i];
    }
    return sum;
  }

  /** Stable sigmoid implementation to avoid overflow for large magnitudes. */
  private double sigmoid(double z) {
    if (z >= 0) {
      double exp = Math.exp(-z);
      return 1.0 / (1.0 + exp);
    }
    double exp = Math.exp(z);
    return exp / (1.0 + exp);
  }

  /** Immutable inference result. */
  public record InferenceResult(double confidenceScore, boolean c2paUsed, String modelVersion) { }
}
