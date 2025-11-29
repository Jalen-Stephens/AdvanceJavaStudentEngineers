package dev.coms4156.project.metadetect.service;

import dev.coms4156.project.metadetect.c2pa.C2paToolInvoker.C2paMetadata;
import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;


/**
 * FeatureExtractor
 * This class extracts all OpenCV-based forensic features used by the future ML model.
 * All functions must return stable, numeric, ML-friendly primitives (double or int).
 * OPEN CV FEATURES IMPLEMENTED:
 *  - Laplacian variance               (sharpness / smoothness)
 *  - Noise residual stddev            (blur vs natural sensor noise)
 *  - Edge density                     (Canny edge fraction)
 *  - High/Low frequency ratio         (DFT-based frequency analysis)
 *  - Saturation entropy               (color distribution entropy)
 *  - Geometry                         (width, height, aspect ratio)
 * NOTE: C2PA metadata is obtained separately via C2paToolInvoker. This class does
 * not call C2PA directly, but is designed to combine its results into the final feature vector.
 */
public class FeatureExtractor {

  static {
    // Load native OpenCV library packaged with openpnp/opencv.
    try {
      OpenCV.loadLocally();
    } catch (Throwable t) {
      // Fallback to shared loader if local extraction is not available.
      try {
        OpenCV.loadShared();
      } catch (Throwable ignored) {
        // Last resort: try the default system library name.
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
      }
    }
  }

  /**
   * Extract all ML features (OpenCV + C2PA).
   *
   * @param imagePath filesystem path to the image
   * @param c2pa pre-extracted C2PA metadata
   * @return array of all features in fixed order
   */
  public double[] extractAllFeatures(String imagePath, C2paMetadata c2pa) {

    Mat img = Imgcodecs.imread(imagePath);
    if (img.empty()) {
      return generateEmpty(c2pa);
    }

    double lapVar      = laplacianVariance(img);
    double noiseStd    = noiseEstimate(img);
    double edgeDensity = edgeDensity(img);
    double freqRatio   = highLowFreqRatio(img);
    double satEntropy  = saturationEntropy(img);

    int width          = img.cols();
    int height         = img.rows();
    double aspectRatio = aspectRatio(width, height);

    return new double[] {
        lapVar,
        noiseStd,
        edgeDensity,
        freqRatio,
        satEntropy,
        width,
        height,
        aspectRatio,
        c2pa.getc2paHasManifest(),
        c2pa.getc2paManifestCount(),
        c2pa.getc2paClaimGeneratorIsAi(),
        c2pa.getc2paErrorFlag()
    };
  }

  /**
   * Generate an empty feature vector with only C2PA fields populated.
   *
   * @param c2pa pre-extracted C2PA metadata
   * @return array of all features in fixed order
   */
  private double[] generateEmpty(C2paMetadata c2pa) {
    return new double[]{
        0, 0, 0, 0, 0, 0, 0, 0,
        c2pa.getc2paHasManifest(),
        c2pa.getc2paManifestCount(),
        c2pa.getc2paClaimGeneratorIsAi(),
        c2pa.getc2paErrorFlag()
    };
  }

  /** Computes aspect ratio while guarding against zero height. */
  private static double aspectRatio(int width, int height) {
    return height == 0 ? 0.0 : (double) width / height;
  }

  /**
   * Laplacian Variance (Sharpness).
   *
   * @param img input image matrix
   * @return variance of Laplacian
   */
  public double laplacianVariance(Mat img) {
    Mat gray = new Mat();
    Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

    Mat lap = new Mat();
    Imgproc.Laplacian(gray, lap, CvType.CV_64F);

    MatOfDouble mean = new MatOfDouble();
    MatOfDouble std  = new MatOfDouble();
    Core.meanStdDev(lap, mean, std);

    return Math.pow(std.get(0, 0)[0], 2);
  }

  /**
   * Noise Estimate (StdDev of Noise Residual).
   *
   * @param img input image matrix
   * @return standard deviation of noise residual
   */
  public double noiseEstimate(Mat img) {
    Mat gray = new Mat();
    Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

    Mat blur = new Mat();
    Imgproc.GaussianBlur(gray, blur, new Size(7, 7), 0);

    Mat diff = new Mat();
    Core.absdiff(gray, blur, diff);

    MatOfDouble mean = new MatOfDouble();
    MatOfDouble std  = new MatOfDouble();
    Core.meanStdDev(diff, mean, std);

    return std.get(0, 0)[0];
  }

  /**
   * Edge Density (Canny Edge Fraction).
   *
   * @param img input image matrix
   * @return fraction of edge pixels
   */
  public double edgeDensity(Mat img) {
    Mat gray = new Mat();
    Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

    Mat edges = new Mat();
    Imgproc.Canny(gray, edges, 100, 200);

    double nonZero = Core.countNonZero(edges);
    double total   = edges.rows() * edges.cols();

    return nonZero / total;
  }

  /**
   * High/Low Frequency Ratio (DFT-based).
   *
   * @param img input image matrix
   * @return ratio of high to low frequency magnitudes
   */
  public double highLowFreqRatio(Mat img) {
    Mat gray = new Mat();
    Imgproc.cvtColor(img, gray, Imgproc.COLOR_BGR2GRAY);

    gray.convertTo(gray, CvType.CV_32F);

    List<Mat> channels = new ArrayList<>();
    channels.add(gray);
    channels.add(Mat.zeros(gray.size(), CvType.CV_32F));

    Mat complex = new Mat();
    Core.merge(channels, complex);
    Core.dft(complex, complex);

    // Split real and imaginary
    List<Mat> comps = new ArrayList<>();
    Core.split(complex, comps);

    Mat mag = new Mat();
    Core.magnitude(comps.get(0), comps.get(1), mag);

    int cx = mag.cols() / 2;
    int cy = mag.rows() / 2;

    double rlow  = Math.min(cx, cy) * 0.25;
    double rhigh = Math.min(cx, cy) * 0.75;

    double low = 0;
    double high = 0;

    for (int y = 0; y < mag.rows(); y++) {
      for (int x = 0; x < mag.cols(); x++) {
        double dx = x - cx;
        double dy = y - cy;
        double d = Math.sqrt(dx * dx + dy * dy);

        double v = mag.get(y, x)[0];

        if (d < rlow) {
          low  += v;
        } else if (d > rhigh) {
          high += v;
        }
      }
    }

    return low == 0 ? 0 : high / low;
  }

  /**
   * Saturation Entropy.
   *
   * @param img input image matrix
   * @return entropy of saturation channel
   */
  public double saturationEntropy(Mat img) {
    Mat hsv = new Mat();
    Imgproc.cvtColor(img, hsv, Imgproc.COLOR_BGR2HSV);

    // Split channels: H, S, V
    List<Mat> channels = new ArrayList<>();
    Core.split(hsv, channels);

    Mat sat = channels.get(1);  // S channel

    // Histogram
    Mat hist = new Mat();
    Imgproc.calcHist(
        java.util.Collections.singletonList(sat),
        new MatOfInt(0),
        new Mat(),
        hist,
        new MatOfInt(64),
        new MatOfFloat(0, 256)
    );

    Core.normalize(hist, hist, 1, 0, Core.NORM_L1);

    double entropy = 0;
    for (int i = 0; i < 64; i++) {
      double p = hist.get(i, 0)[0];
      if (p > 1e-8) {
        entropy -= p * Math.log(p);
      }
    }

    return entropy;
  }
}
