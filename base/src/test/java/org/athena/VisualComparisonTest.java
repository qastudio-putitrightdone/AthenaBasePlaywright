package org.athena;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class VisualComparisonTest {

    private static final String HTML = "<div id='box' style='width:200px;height:100px;background:%s'></div>"
            + "<span id='clock'>%s</span>";

    private Playwright playwright;
    private Browser browser;
    private Page page;
    private VisualOptions options;

    @BeforeClass
    public void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        page = browser.newPage();
    }

    @AfterClass(alwaysRun = true)
    public void close() {
        browser.close();
        playwright.close();
    }

    @BeforeMethod
    public void freshDirs() throws IOException {
        Path root = Files.createTempDirectory("visual");
        options = new VisualOptions().setBaselineDir(root.resolve("baselines")).setOutputDir(root.resolve("results"));
    }

    @Test
    public void firstRunCreatesBaselineThenMatches() {
        page.setContent(String.format(HTML, "blue", "10:00"));
        VisualComparison.compareWithBaseline(page, "home page", options);
        assertTrue(Files.exists(options.getBaselineDir().resolve("home_page.png")));

        VisualComparison.compareWithBaseline(page, "home page", options);
    }

    @Test
    public void changedUiFailsAndWritesDiff() {
        page.setContent(String.format(HTML, "blue", "10:00"));
        VisualComparison.compareWithBaseline(page, "box", options);

        page.setContent(String.format(HTML, "red", "10:00"));
        assertThrows(VisualMismatchException.class, () -> VisualComparison.compareWithBaseline(page, "box", options));
        assertTrue(Files.exists(options.getOutputDir().resolve("box-diff.png")));
        assertTrue(Files.exists(options.getOutputDir().resolve("box-actual.png")));
    }

    @Test
    public void maskedDynamicContentIsIgnored() {
        page.setContent(String.format(HTML, "blue", "10:00"));
        VisualOptions masked = options.setMask(List.of(page.locator("#clock")));
        VisualComparison.compareWithBaseline(page, "masked", masked);

        page.setContent(String.format(HTML, "blue", "23:59"));
        VisualComparison.compareWithBaseline(page, "masked", options.setMask(List.of(page.locator("#clock"))));
    }

    @Test
    public void storedSnapshotIsComparedAndNeverCreated() throws IOException {
        page.setContent(String.format(HTML, "blue", "10:00"));
        Path snapshot = options.getBaselineDir().resolve("stored.png");
        assertThrows(VisualMismatchException.class, () -> VisualComparison.compareWithSnapshot(page, snapshot, options));
        assertFalse(Files.exists(snapshot));

        Files.createDirectories(snapshot.getParent());
        Files.write(snapshot, page.locator("#box").screenshot());
        VisualComparison.compareWithSnapshot(page.locator("#box"), snapshot, options);

        page.setContent(String.format(HTML, "green", "10:00"));
        assertThrows(VisualMismatchException.class,
                () -> VisualComparison.compareWithSnapshot(page.locator("#box"), snapshot, options));
    }

    @Test
    public void toleranceAllowsSmallDifferences() {
        page.setContent(String.format(HTML, "blue", "10:00"));
        byte[] expected = page.locator("#box").screenshot();
        page.setContent(String.format(HTML, "rgb(0,0,250)", "10:00"));
        byte[] actual = page.locator("#box").screenshot();

        assertTrue(VisualComparison.compare(expected, actual, new VisualOptions()).matches());
        VisualComparison.Result strict = VisualComparison.compare(expected, actual, new VisualOptions().setThreshold(0));
        assertFalse(strict.matches());
        assertEquals(strict.diffPixels(), strict.totalPixels());
    }
}
