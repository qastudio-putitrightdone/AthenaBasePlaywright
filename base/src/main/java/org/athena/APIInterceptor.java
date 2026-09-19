package org.athena;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class APIInterceptor {

    private static final Gson GSON = new Gson();

    public static ThreadLocal<JsonElement> apiResponse = new ThreadLocal<>();

    public static JsonElement waitForResponse(Page page, String predicateUrl, int statusCode, Runnable runnableAction) {
        page.waitForResponse( response -> {
            if (response.url().contains(predicateUrl) && response.status() == statusCode) {
                apiResponse.set(JsonParser.parseString(response.text()).getAsJsonObject());
                return true;
            } else {
                return false;
            }
        }, runnableAction);
        return apiResponse.get();
    }

    public static JsonElement waitForResponse(Page page, String predicateUrl, Runnable runnableAction) {
        page.waitForResponse( response -> {
            if (response.url().contains(predicateUrl)) {
                apiResponse.set(JsonParser.parseString(response.text()).getAsJsonObject());
                return true;
            } else {
                return false;
            }
        }, runnableAction);
        return apiResponse.get();
    }

    /**
     * Mocks the entire response: the request never reaches the server. The mock stays registered
     * until {@link #clearMocks(Page)}.
     */
    public static void mockResponse(Page page, String urlContains, int statusCode, JsonElement body) {
        page.route(url -> url.contains(urlContains), mockHandler(statusCode, body));
    }

    public static void mockResponse(Page page, String urlContains, JsonElement body) {
        mockResponse(page, urlContains, 200, body);
    }

    /**
     * Mocks the entire response for the duration of {@code runnableAction} only: registers the mock,
     * runs the action, waits for the matching call to complete, then removes the mock.
     */
    public static void mockResponse(Page page, String urlContains, int statusCode, JsonElement body, Runnable runnableAction) {
        runWithRoute(page, urlContains, mockHandler(statusCode, body), runnableAction);
    }

    public static void mockResponse(Page page, String urlContains, JsonElement body, Runnable runnableAction) {
        mockResponse(page, urlContains, 200, body, runnableAction);
    }

    /**
     * Patches the real response: the request goes to the server and the JSON body is modified by
     * the patcher before it reaches the browser. Status and headers of the real response are kept.
     * The patch stays registered until {@link #clearMocks(Page)}.
     */
    public static void patchResponse(Page page, String urlContains, Consumer<JsonElement> patcher) {
        page.route(url -> url.contains(urlContains), patchHandler(patcher));
    }

    /**
     * Patches individual nodes of the real response, addressed by path such as
     * {@code "data.user.name"} or {@code "items[0].price"}. Values are converted to JSON with Gson
     * (String, Number, Boolean, null, Map, List or JsonElement).
     */
    public static void patchResponse(Page page, String urlContains, Map<String, Object> patches) {
        patchResponse(page, urlContains, nodePatcher(patches));
    }

    /**
     * Patches the real response for the duration of {@code runnableAction} only: registers the patch,
     * runs the action, waits for the matching call to complete, then removes the patch.
     */
    public static void patchResponse(Page page, String urlContains, Consumer<JsonElement> patcher, Runnable runnableAction) {
        runWithRoute(page, urlContains, patchHandler(patcher), runnableAction);
    }

    public static void patchResponse(Page page, String urlContains, Map<String, Object> patches, Runnable runnableAction) {
        patchResponse(page, urlContains, nodePatcher(patches), runnableAction);
    }

    /**
     * Removes every mock and patch registered on the page.
     */
    public static void clearMocks(Page page) {
        page.unrouteAll();
    }

    private static Consumer<Route> mockHandler(int statusCode, JsonElement body) {
        return route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(statusCode)
                .setContentType("application/json")
                .setBody(body.toString()));
    }

    private static Consumer<Route> patchHandler(Consumer<JsonElement> patcher) {
        return route -> {
            APIResponse response = route.fetch();
            JsonElement body = JsonParser.parseString(response.text());
            patcher.accept(body);
            route.fulfill(new Route.FulfillOptions()
                    .setResponse(response)
                    .setBody(body.toString()));
        };
    }

    private static Consumer<JsonElement> nodePatcher(Map<String, Object> patches) {
        return body -> patches.forEach((path, value) -> setAtPath(body, path, GSON.toJsonTree(value)));
    }

    private static void runWithRoute(Page page, String urlContains, Consumer<Route> handler, Runnable runnableAction) {
        Predicate<String> matcher = url -> url.contains(urlContains);
        page.route(matcher, handler);
        try {
            page.waitForResponse(response -> response.url().contains(urlContains), runnableAction);
        } finally {
            page.unroute(matcher, handler);
        }
    }

    private static void setAtPath(JsonElement root, String path, JsonElement value) {
        List<String> tokens = Arrays.stream(path.replace("[", ".[").split("\\."))
                .filter(token -> !token.isEmpty())
                .toList();
        JsonElement current = root;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            boolean last = i == tokens.size() - 1;
            if (token.startsWith("[")) {
                JsonArray array = current.getAsJsonArray();
                int index = Integer.parseInt(token.substring(1, token.length() - 1));
                if (last) {
                    array.set(index, value);
                } else {
                    current = array.get(index);
                }
            } else {
                JsonObject object = current.getAsJsonObject();
                if (last) {
                    object.add(token, value);
                } else {
                    current = object.get(token);
                    if (current == null) {
                        throw new IllegalArgumentException("Path '" + path + "' not found at '" + token + "'");
                    }
                }
            }
        }
    }
}
