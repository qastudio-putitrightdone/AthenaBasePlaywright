package org.athena;

import com.microsoft.playwright.Locator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Options for {@link VisualComparison}. All setters return {@code this} so options can be chained:
 * <pre>
 * new VisualOptions().setMaxDiffPixelRatio(0.01).setMask(List.of(page.locator(".clock")))
 * </pre>
 * Baseline and output directories default to the system properties {@code visual.baselineDir} and
 * {@code visual.outputDir}, falling back to {@code src/test/resources/visual-baselines} and
 * {@code target/visual-results}.
 */
public class VisualOptions {

    private double threshold = 0.1;
    private double maxDiffPixelRatio = 0;
    private int maxDiffPixels = 0;
    private boolean fullPage = false;
    private List<Locator> mask = new ArrayList<>();
    private Path baselineDir = Paths.get(System.getProperty("visual.baselineDir", "src/test/resources/visual-baselines"));
    private Path outputDir = Paths.get(System.getProperty("visual.outputDir", "target/visual-results"));

    public double getThreshold() {
        return threshold;
    }

    /**
     * Per-pixel color tolerance from 0 (exact) to 1 (anything matches). A pixel counts as different
     * when any RGBA channel differs by more than {@code threshold * 255}. Default 0.1.
     */
    public VisualOptions setThreshold(double threshold) {
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("threshold must be between 0 and 1");
        }
        this.threshold = threshold;
        return this;
    }

    public double getMaxDiffPixelRatio() {
        return maxDiffPixelRatio;
    }

    /**
     * Allowed share of differing pixels, from 0 to 1. Default 0.
     */
    public VisualOptions setMaxDiffPixelRatio(double maxDiffPixelRatio) {
        if (maxDiffPixelRatio < 0 || maxDiffPixelRatio > 1) {
            throw new IllegalArgumentException("maxDiffPixelRatio must be between 0 and 1");
        }
        this.maxDiffPixelRatio = maxDiffPixelRatio;
        return this;
    }

    public int getMaxDiffPixels() {
        return maxDiffPixels;
    }

    /**
     * Allowed number of differing pixels. Default 0. The comparison passes when either this or
     * {@link #setMaxDiffPixelRatio(double)} is satisfied.
     */
    public VisualOptions setMaxDiffPixels(int maxDiffPixels) {
        if (maxDiffPixels < 0) {
            throw new IllegalArgumentException("maxDiffPixels must not be negative");
        }
        this.maxDiffPixels = maxDiffPixels;
        return this;
    }

    public boolean isFullPage() {
        return fullPage;
    }

    /**
     * Captures the full scrollable page instead of the viewport. Ignored for locator screenshots.
     */
    public VisualOptions setFullPage(boolean fullPage) {
        this.fullPage = fullPage;
        return this;
    }

    public List<Locator> getMask() {
        return mask;
    }

    /**
     * Elements to cover with a solid box before capturing, for dynamic content such as dates or ads.
     */
    public VisualOptions setMask(List<Locator> mask) {
        this.mask = new ArrayList<>(mask);
        return this;
    }

    public Path getBaselineDir() {
        return baselineDir;
    }

    public VisualOptions setBaselineDir(Path baselineDir) {
        this.baselineDir = baselineDir;
        return this;
    }

    public Path getOutputDir() {
        return outputDir;
    }

    /**
     * Where actual and diff images are written when a comparison fails.
     */
    public VisualOptions setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
        return this;
    }
}
