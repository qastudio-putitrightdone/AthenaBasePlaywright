# Athena Base Playwright

Shared Java Playwright base for Athena test frameworks. Put common browser, page and API helpers here once so downstream frameworks depend on this module instead of duplicating the code.

- Playwright for Java **1.63.0**
- Java 21, Maven
- TestNG and Allure (declared in the parent pom)

## Using it in a framework

Add the `base` module as a dependency:

```xml
<dependency>
    <groupId>org.athena.playwright</groupId>
    <artifactId>base</artifactId>
    <version>1.1.0</version>
</dependency>
```

Playwright and Gson come in transitively. Do not redeclare them with a different version in the consuming framework, or you can end up with a driver and browser mismatch.

After upgrading, install the matching browsers once:

```
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install"
```

## What is in the box

| Class | Purpose |
|---|---|
| `LaunchBrowser` | Starts a browser and opens the application; optionally reuses a storage state. |
| `BasePage` | Base class for page objects: waits, visibility checks, click/type, child locators, screenshot attachment. |
| `APIInterceptor` | Reads, mocks and patches API responses (see below). |
| `ReadConfigData` | Loads a properties file. |
| `LocatorNotFoundException`, `FailedToLoadPageException` | Framework exceptions. |

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

## Changing this module

Consumers pick up whatever version they depend on, so:

- Bump the version in the root `pom.xml` and `base/pom.xml` for every change downstream should notice. A Playwright upgrade is at least a minor bump.
- Keep helpers generic. Anything specific to one application belongs in that framework.
- Playwright 1.63 dropped Ubuntu 20.04 support. Check CI images before rolling out.
