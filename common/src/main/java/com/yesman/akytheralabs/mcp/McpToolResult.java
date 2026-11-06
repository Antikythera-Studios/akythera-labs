package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Base64;

/// The `tools/call` result of an [McpTool]: a list of content blocks, and whether the call failed.
public record McpToolResult(JsonArray content, boolean isError) {
    public static McpToolResult text(final String text) {
        JsonArray content = new JsonArray();
        content.add(textBlock(text));

        return new McpToolResult(content, false);
    }

    public static McpToolResult error(final String message) {
        JsonArray content = new JsonArray();
        content.add(textBlock(message));

        return new McpToolResult(content, true);
    }

    /// A PNG image followed by a text caption.
    public static McpToolResult image(final byte[] png, final String caption) {
        JsonObject image = new JsonObject();
        image.addProperty("type", "image");
        image.addProperty("data", Base64.getEncoder().encodeToString(png));
        image.addProperty("mimeType", "image/png");

        JsonArray content = new JsonArray();
        content.add(image);
        content.add(textBlock(caption));

        return new McpToolResult(content, false);
    }

    private static JsonObject textBlock(final String text) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", text);

        return block;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.add("content", content);
        json.addProperty("isError", isError);

        return json;
    }
}
