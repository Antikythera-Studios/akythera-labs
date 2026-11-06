package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/// A tool [AkytheraMcpServer] lists to MCP clients and runs on `tools/call`.
///
/// @param name        the id a client calls the tool by, in `snake_case`
/// @param description what the tool does, read by the AI agent to decide when to call it
/// @param inputSchema the JSON schema of the `arguments` object
/// @param handler     runs the tool on a server connection thread
public record McpTool(String name, String description, JsonObject inputSchema, Handler handler) {
    /// Creates a tool whose input schema is written as JSON text.
    public static McpTool of(final String name, final String description, final String inputSchemaJson, final Handler handler) {
        return new McpTool(name, description, JsonParser.parseString(inputSchemaJson).getAsJsonObject(), handler);
    }

    @FunctionalInterface
    public interface Handler {
        /// Runs the tool. A thrown exception reaches the client as an error result carrying its message.
        McpToolResult call(JsonObject arguments) throws Exception;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", description);
        json.add("inputSchema", inputSchema);

        return json;
    }
}
