# Athena Base Playwright

Shared Java Playwright base for Athena test frameworks. Put common browser, page and API helpers here once so downstream frameworks depend on this module instead of duplicating the code.

- Playwright for Java **1.63.0**
- Java 21, Maven
- TestNG and Allure (declared in the parent pom)

## Using it in a framework

Add the `base` module as a dependency:

```xml
<dependency>
    <groupId>io.github.qastudio-putitrightdone</groupId>
    <artifactId>base</artifactId>
    <version>1.2.0</version>
</dependency>
```

It is published on Maven Central, so no extra repository or token is needed.

Playwright and Gson come in transitively. Do not redeclare them with a different version in the consuming framework, or you can end up with a driver and browser mismatch.

After upgrading, install the matching browsers once:

```
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

## What is in the box

| Class | Purpose |
|---|---|
| `LaunchBrowser` | Starts a browser and opens the application; optionally reuses a storage state. |
| `BasePage` | Base class for page objects: waits, visibility checks, click/type, child locators, screenshot attachment, visual checks. |
| `APIInterceptor` | Reads, mocks and patches API responses (see below). |
| `VisualComparison`, `VisualOptions` | Screenshot comparison against a baseline or a stored snapshot (see below). |
| `ReadConfigData` | Loads a properties file. |
| `LocatorNotFoundException`, `FailedToLoadPageException`, `VisualMismatchException` | Framework exceptions. |

## APIInterceptor

URLs are matched with `contains`. Register mocks and patches **before** the action that triggers the call.

### Read a response

```java
JsonElement body = APIInterceptor.waitForResponse(page, "/api/orders", 200,
        () -> page.getByText("Load orders").click());
```

### Mock the entire response

The request never reaches the server. Use it for error states or data that is hard to create.

```java
APIInterceptor.mockResponse(page, "/api/users", 500,
        JsonParser.parseString("{\"error\":\"boom\"}"));

APIInterceptor.mockResponse(page, "/api/users",   // status defaults to 200
        JsonParser.parseString("{\"users\":[]}"));
```

### Patch some nodes of the real response

The request goes to the server. Only the nodes you name change, and the real status and headers are kept.

```java
// By path: dotted keys and [index] for arrays
APIInterceptor.patchResponse(page, "/api/orders", Map.of(
        "data.status", "CANCELLED",
        "data.items[0].price", 0));

// Custom logic
APIInterceptor.patchResponse(page, "/api/orders", body ->
        body.getAsJsonObject().getAsJsonObject("data").remove("discount"));
```

A path that does not exist throws `IllegalArgumentException`, so a typo fails the test instead of silently doing nothing. The last node of a path may be new, so you can add a field.

### Scope a mock or patch to one action

Every `mockResponse` and `patchResponse` overload also takes a trailing `Runnable`, the action that triggers the call. The helper registers the route, runs the action, waits for the matching call to finish, then removes the route, so nothing leaks into later steps.

```java
APIInterceptor.patchResponse(page, "/api/orders",
        Map.of("data.status", "CANCELLED"),
        () -> page.getByText("Load orders").click());

APIInterceptor.mockResponse(page, "/api/users", 500,
        JsonParser.parseString("{\"error\":\"boom\"}"),
        () -> page.getByText("Load users").click());
```

Without the `Runnable`, the mock or patch stays registered until `clearMocks`.

### Clean up

```java
APIInterceptor.clearMocks(page);   // removes every mock and patch on the page
```

If several routes match the same URL, the most recently registered one runs first.

## Visual comparison

Compares screenshots pixel by pixel. Animations and the text caret are disabled during capture so runs are stable.

### Baseline comparison

The first run saves the screenshot as the baseline and passes. Later runs compare against it.

```java
VisualComparison.compareWithBaseline(page, "login page");               // full page
VisualComparison.compareWithBaseline(page.locator("#header"), "header"); // one element

// from a page object
compareWithBaseline("login page");
compareWithBaseline("#header", "header", new VisualOptions());
```

Baselines go to `src/test/resources/visual-baselines/<name>.png`. Commit them. After an intended UI change, refresh them with:

```
mvn test -DupdateBaselines=true
```

### Stored snapshot comparison

Compares against a snapshot file you already keep. The file is never created or overwritten, and a missing file fails the check (the actual screenshot is saved so you can review it and put it in place).

```java
VisualComparison.compareWithSnapshot(page, Path.of("src/test/resources/snapshots/cart.png"));
compareWithSnapshot(cartLocator, Path.of("src/test/resources/snapshots/cart.png"), new VisualOptions());
```

### Options

```java
new VisualOptions()
        .setThreshold(0.1)                         // per-pixel color tolerance, 0 to 1 (default 0.1)
        .setMaxDiffPixels(50)                      // allowed differing pixels (default 0)
        .setMaxDiffPixelRatio(0.01)                // or allowed share of differing pixels (default 0)
        .setMask(List.of(page.locator(".clock")))  // cover dynamic content
        .setFullPage(true)                         // whole scrollable page (page screenshots only)
        .setBaselineDir(Path.of("..."))            // or -Dvisual.baselineDir
        .setOutputDir(Path.of("..."));             // or -Dvisual.outputDir, default target/visual-results
```

### On a mismatch

Expected, actual and diff images are attached to Allure and written to `target/visual-results/`, and a `VisualMismatchException` is thrown. The diff shows changed pixels in red over a faded copy of the expected image. Different image sizes always fail.

Rendering differs between operating systems and browsers (fonts, anti-aliasing), so create baselines on the same platform the tests run on in CI, or include the browser name in the baseline name.

## Releasing to Maven Central

Publishing is done by `.github/workflows/publish.yml` when a GitHub release is published (or run manually from the Actions tab).

One-time setup:

1. Create an account at <https://central.sonatype.com> and register the namespace `io.github.qastudio-putitrightdone` (verified by creating the temporary repo Central asks for in the GitHub org).
2. Generate a user token there (Account > Generate User Token).
3. Create a GPG key (`gpg --full-generate-key`) and publish the public key: `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`.
4. Add these repository secrets in GitHub:
   - `CENTRAL_USERNAME`, `CENTRAL_PASSWORD`: the Central token pair
   - `GPG_PRIVATE_KEY`: output of `gpg --armor --export-secret-keys <KEY_ID>`
   - `GPG_PASSPHRASE`: the key passphrase

To release: set the version in the root `pom.xml` and `base/pom.xml`, commit, then create a GitHub release. A version on Central can never be changed or removed, so bump the version for every release.

## Changing this module

Consumers pick up whatever version they depend on, so:

- Bump the version in the root `pom.xml` and `base/pom.xml` for every change downstream should notice. A Playwright upgrade is at least a minor bump.
- Keep helpers generic. Anything specific to one application belongs in that framework.
- Playwright 1.63 dropped Ubuntu 20.04 support. Check CI images before rolling out.
