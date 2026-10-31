package org.athena;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotAnimations;
import com.microsoft.playwright.options.ScreenshotCaret;
import io.qameta.allure.Allure;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Screenshot based visual testing.
 * <ul>
 *     <li>{@code compareWithBaseline}: the first run saves the screenshot as the baseline and passes,
 *     later runs compare against it. Run with {@code -DupdateBaselines=true} to overwrite baselines.</li>
 *     <li>{@code compareWithSnapshot}: compares against a snapshot file you already store (for example
 *     under {@code src/test/resources}). The snapshot is never created or overwritten; a missing
 *     snapshot fails the check.</li>
 * </ul>
 * On a mismatch the expected, actual and diff images are attached to Allure, written to
 * {@link VisualOptions#getOutputDir()}, and a {@link VisualMismatchException} is thrown.
 */
public class VisualComparison {

    private static final int DIFF_COLOR = 0xFFFF0000;

    public record Result(boolean matches, long diffPixels, long totalPixels, boolean sizeMismatch, byte[] diffImage) {

        public double diffRatio() {
            return totalPixels == 0 ? 0 : (double) diffPixels / totalPixels;
        }
    }

    public static void compareWithBaseline(Page page, String name) {
        compareWithBaseline(page, name, new VisualOptions());
    }

    public static void compareWithBaseline(Page page, String name, VisualOptions options) {
        compareWithBaseline(capture(page, options), name, options);
    }

    public static void compareWithBaseline(Locator locator, String name) {
        compareWithBaseline(locator, name, new VisualOptions());
    }

    public static void compareWithBaseline(Locator locator, String name, VisualOptions options) {
        compareWithBaseline(capture(locator, options), name, options);
    }

    public static void compareWithSnapshot(Page page, Path snapshot) {
        compareWithSnapshot(page, snapshot, new VisualOptions());
    }

    public static void compareWithSnapshot(Page page, Path snapshot, VisualOptions options) {
        compareWithSnapshot(capture(page, options), snapshot, options);
    }

    public static void compareWithSnapshot(Locator locator, Path snapshot) {
        compareWithSnapshot(locator, snapshot, new VisualOptions());
    }

    public static void compareWithSnapshot(Locator locator, Path snapshot, VisualOptions options) {
        compareWithSnapshot(capture(locator, options), snapshot, options);
    }

    /**
     * Compares two PNG images pixel by pixel without asserting anything.
     */
    public static Result compare(byte[] expectedPng, byte[] actualPng, VisualOptions options) {
        BufferedImage expected = decode(expectedPng);
        BufferedImage actual = decode(actualPng);
        int width = Math.max(expected.getWidth(), actual.getWidth());
        int height = Math.max(expected.getHeight(), actual.getHeight());
        boolean sizeMismatch = expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight();
        int tolerance = (int) Math.round(options.getThreshold() * 255);

        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        long diffPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean inExpected = x < expected.getWidth() && y < expected.getHeight();
                boolean inActual = x < actual.getWidth() && y < actual.getHeight();
                if (inExpected && inActual && pixelsMatch(expected.getRGB(x, y), actual.getRGB(x, y), tolerance)) {
                    diff.setRGB(x, y, faded(expected.getRGB(x, y)));
                } else {
                    diff.setRGB(x, y, DIFF_COLOR);
                    diffPixels++;
                }
            }
        }

        long totalPixels = (long) width * height;
        boolean withinLimits = diffPixels <= options.getMaxDiffPixels()
                || (double) diffPixels / totalPixels <= options.getMaxDiffPixelRatio();
        return new Result(!sizeMismatch && withinLimits, diffPixels, totalPixels, sizeMismatch, encode(diff));
    }

    private static void compareWithBaseline(byte[] actual, String name, VisualOptions options) {
        Path baseline = options.getBaselineDir().resolve(fileName(name));
        if (!Files.exists(baseline) || Boolean.getBoolean("updateBaselines")) {
            write(baseline, actual);
            Allure.addAttachment(name + " - baseline", "image/png", new ByteArrayInputStream(actual), "png");
            Allure.step("Saved visual baseline: " + baseline);
            return;
        }
        assertMatches(read(baseline), actual, name, baseline, options);
    }

    private static void compareWithSnapshot(byte[] actual, Path snapshot, VisualOptions options) {
        String name = snapshot.getFileName().toString().replaceFirst("\\.png$", "");
        if (!Files.exists(snapshot)) {
            Path actualPath = write(options.getOutputDir().resolve(name + "-actual.png"), actual);
            Allure.addAttachment(name + " - actual", "image/png", new ByteArrayInputStream(actual), "png");
            throw new VisualMismatchException("Snapshot not found: " + snapshot + ". Actual screenshot saved to " + actualPath);
        }
        assertMatches(read(snapshot), actual, name, snapshot, options);
    }

    private static void assertMatches(byte[] expected, byte[] actual, String name, Path expectedPath, VisualOptions options) {
        Result result = compare(expected, actual, options);
        if (result.matches()) {
            Allure.step(String.format("Visual check '%s' passed (%d of %d pixels differ)", name, result.diffPixels(), result.totalPixels()));
            return;
        }

        Allure.addAttachment(name + " - expected", "image/png", new ByteArrayInputStream(expected), "png");
        Allure.addAttachment(name + " - actual", "image/png", new ByteArrayInputStream(actual), "png");
        Allure.addAttachment(name + " - diff", "image/png", new ByteArrayInputStream(result.diffImage()), "png");
        String safeName = fileName(name).replaceFirst("\\.png$", "");
        Path actualPath = write(options.getOutputDir().resolve(safeName + "-actual.png"), actual);
        Path diffPath = write(options.getOutputDir().resolve(safeName + "-diff.png"), result.diffImage());

        String reason = result.sizeMismatch()
                ? "image sizes differ"
                : String.format("%d pixels (%.2f%%) differ", result.diffPixels(), result.diffRatio() * 100);
        throw new VisualMismatchException(String.format("Visual check '%s' failed: %s. Expected: %s, actual: %s, diff: %s",
                name, reason, expectedPath, actualPath, diffPath));
    }

    private static byte[] capture(Page page, VisualOptions options) {
        return page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(options.isFullPage())
                .setMask(options.getMask())
                .setAnimations(ScreenshotAnimations.DISABLED)
                .setCaret(ScreenshotCaret.HIDE));
    }

    private static byte[] capture(Locator locator, VisualOptions options) {
        return locator.screenshot(new Locator.ScreenshotOptions()
                .setMask(options.getMask())
                .setAnimations(ScreenshotAnimations.DISABLED)
                .setCaret(ScreenshotCaret.HIDE));
    }

    private static boolean pixelsMatch(int expected, int actual, int tolerance) {
        if (expected == actual) {
            return true;
        }
        for (int shift = 0; shift <= 24; shift += 8) {
            if (Math.abs(((expected >>> shift) & 0xFF) - ((actual >>> shift) & 0xFF)) > tolerance) {
                return false;
            }
        }
        return true;
    }

    /**
     * Grayscale, low-contrast copy of a matching pixel so the red differences stand out.
     */
    private static int faded(int argb) {
        int gray = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
        int light = 255 - (255 - gray) / 4;
        return 0xFF000000 | (light << 16) | (light << 8) | light;
    }

    private static String fileName(String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.endsWith(".png") ? safe : safe + ".png";
    }

    private static BufferedImage decode(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                throw new IllegalArgumentException("Data is not a readable image");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path write(Path path, byte[] data) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return Files.write(path, data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
