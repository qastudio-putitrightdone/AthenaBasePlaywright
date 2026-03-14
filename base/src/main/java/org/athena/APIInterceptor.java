package org.athena;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Page;

public class APIInterceptor {

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
}
