package org.athena;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;
import java.util.List;

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
                throw new LocatorNotFoundException("Element with locator: " + arg + " was not visible within the timeout period.");
            }
        } else if (arg instanceof Locator) {
            try {
                page.waitForCondition(() -> ((Locator) arg).isVisible());
                return true;
            } catch (TimeoutError e) {
                throw new LocatorNotFoundException("Element with locator: " + arg + " was not visible within the timeout period.");
            }
        }
        return false;
    }

    public <T> boolean waitForElementToDisplay(T arg, Double timeoutInSeconds) {
        if (arg instanceof String) {
            try {
                page.waitForCondition(() -> page.locator((String) arg).isVisible(),
                        new Page.WaitForConditionOptions().setTimeout(timeoutInSeconds));
                return true;
            } catch (TimeoutError e) {
                throw new LocatorNotFoundException("Element with locator: " + arg + " was not visible within the timeout period.");
            }
        } else if (arg instanceof Locator) {
            try {
                page.waitForCondition(() -> ((Locator) arg).isVisible(),
                        new Page.WaitForConditionOptions().setTimeout(timeoutInSeconds));
                return true;
            } catch (TimeoutError e) {
                throw new LocatorNotFoundException("Element with locator: " + arg + " was not visible within the timeout period.");
            }
        }
        return false;
    }

    public <T> boolean waitForElementToDisplayWTThrow(T arg, Double timeoutInSeconds) {
        if (arg instanceof String) {
            try {
                page.waitForCondition(() -> page.locator((String) arg).isVisible(),
                        new Page.WaitForConditionOptions().setTimeout(timeoutInSeconds));
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        } else if (arg instanceof Locator) {
            try {
                page.waitForCondition(() -> ((Locator) arg).isVisible(),
                        new Page.WaitForConditionOptions().setTimeout(timeoutInSeconds));
                return true;
            } catch (TimeoutError e) {
                return false;
            }
        }
        return false;
    }

    public Locator waitForElementToDisplay(String locatorText) {
        page.waitForCondition(() -> page.getByText(locatorText, new Page.GetByTextOptions().setExact(true)).isVisible());
        return page.getByText(locatorText, new Page.GetByTextOptions().setExact(true));
    }

    public void waitForElementToDisappear(String locatorText) {
        page.waitForCondition(() -> !page.getByText(locatorText, new Page.GetByTextOptions().setExact(true)).isVisible());
    }

    public Locator waitForElementWithTimeout(String locatorText, Double timeoutInSeconds) {
        page.waitForCondition(() -> page.getByText(locatorText).isVisible(), new Page.WaitForConditionOptions().setTimeout(timeoutInSeconds));
        return page.getByText(locatorText, new Page.GetByTextOptions().setExact(true));
    }

    public void waitForPageLoad() {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (TimeoutError e) {
            Allure.step("Failed to load the page within the timeout period.");
        }
    }

    public void waitForUrlToContain(String urlFragment) {
        try {
            page.waitForURL(url -> url.contains(urlFragment));
        } catch (TimeoutError e) {
            Allure.step("URL did not contain the expected fragment within the timeout period.");
            throw new FailedToLoadPageException("URL did not contain the expected fragment within the timeout period.");
        }
    }

    public <T> void typeIntoElement(T arg, String textToType) {
        if (arg instanceof String) {
            page.locator((String) arg).clear();
            page.locator((String) arg).fill(textToType);
        } else if (arg instanceof Locator) {
            ((Locator) arg).clear();
            ((Locator) arg).fill(textToType);
        }
    }

    public <T> void clickElement(T arg) {
        if (arg instanceof String) {
            page.locator((String) arg).click();
        } else if (arg instanceof Locator) {
            ((Locator) arg).click();
        }
    }

    public <T> Locator filterLocator(T arg, String filterText) {
        if (arg instanceof String) {
            return page.locator((String) arg).filter(new Locator.FilterOptions().setHasText(filterText));
        } else if (arg instanceof Locator) {
            return ((Locator) arg).filter(new Locator.FilterOptions().setHasText(filterText));
        }

        return null;
    }

    public <T> List<Locator> getChildLocators(T arg, Locator childLocator) {
        if (arg instanceof String) {
            return page.locator((String) arg).locator(childLocator).all();
        } else if (arg instanceof Locator) {
            return ((Locator) arg).locator(childLocator).all();
        }

        return null;
    }

    public <T> List<Locator> getChildLocators(T arg, String childLocator) {
        if (arg instanceof String) {
            return page.locator((String) arg).locator(childLocator).all();
        } else if (arg instanceof Locator) {
            return ((Locator) arg).locator(childLocator).all();
        }

        return null;
    }
}
