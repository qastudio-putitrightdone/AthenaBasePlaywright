package org.athena;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;

public class BasePage {

    public Page page;

    public BasePage(Page page) {
        this.page = page;
    }

    public void attachScreenshot(Page page, String screenshotName) {
        byte[] screenshot = page.screenshot();
        Allure.addAttachment(screenshotName, "image/png", new ByteArrayInputStream(screenshot), "png");
    }

    public <T> boolean isElementVisible(T arg) {
        if (arg instanceof String) {
            try {
                page.waitForCondition(() -> page.locator((String) arg).isVisible());
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        } else if (arg instanceof Locator) {
            try {
                page.waitForCondition(() -> ((Locator) arg).isVisible());
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        }
        return false;
    }

    public <T> boolean isElementVisible(T arg, int timeout) {
        if (arg instanceof String) {
            try {
                page.waitForCondition(() -> page.locator((String) arg).isVisible(),
                        new Page.WaitForConditionOptions().setTimeout(timeout));
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        } else if (arg instanceof Locator) {
            try {
                page.waitForCondition(() -> ((Locator) arg).isVisible(),
                        new Page.WaitForConditionOptions().setTimeout(timeout));
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        }
        return false;
    }

    public <T> boolean waitForElementToDisplay(T arg) {
        if (arg instanceof String) {
            try {
                page.waitForCondition(() -> page.locator((String) arg).isVisible());
                return true;
            } catch (TimeoutError e) {
                throw new RuntimeException("Element with locator: " + arg + " was not visible within the timeout period.");
            }
        } else if (arg instanceof Locator) {
            try {
                page.waitForCondition(() -> ((Locator) arg).isVisible());
                return true;
            } catch (TimeoutError e) {
                throw new RuntimeException("Element with locator: " + arg + " was not visible within the timeout period.");
            }
        }
        return false;
    }

    public void waitForPageLoad() {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (TimeoutError e) {
            Allure.step("Failed to load the page within the timeout period.");
        }
    }
}
