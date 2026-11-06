package com.yesman.akytheralabs.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import com.yesman.akytheralabs.AkytheraLabs;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/// A Model Context Protocol server over the Streamable HTTP transport, letting an AI agent drive the editor
/// and the game through [McpTools].
///
/// Built on plain `java.net` sockets: answers JSON-RPC `POST`s with `application/json` and opens no SSE
/// stream. It binds loopback addresses only, and refuses requests whose `Host` or `Origin` is not a loopback
/// name so a web page cannot reach it through DNS rebinding.
public final class AkytheraMcpServer {
    public static final String DEFAULT_URL = "http://127.0.0.1:25580/mcp";

    /// Supported protocol revisions, newest first.
    private static final List<String> PROTOCOL_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26");
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;
    private static final int IDLE_TIMEOUT_MS = 60_000;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private static final AkytheraMcpServer INSTANCE = new AkytheraMcpServer();

    public static AkytheraMcpServer getInstance() {
        return INSTANCE;
    }

    private @Nullable ServerSocket serverSocket;
    private @Nullable ExecutorService connections;

    /// The URL the server answers on while running.
    private @Nullable URI uri;

    private AkytheraMcpServer() {}

    /// Starts the server on @param `url`, stopping the one already running.
    ///
    /// @throws IllegalArgumentException when the url is not `http://<loopback host>:<port>[/path]`
    public synchronized void start(final String url) throws IOException {
        URI parsed = URI.create(url.trim());

        if (!"http".equalsIgnoreCase(parsed.getScheme())) throw new IllegalArgumentException("The URL must start with http://");
        if (parsed.getHost() == null || parsed.getPort() < 1) throw new IllegalArgumentException("The URL needs a host and a port");

        InetAddress address = InetAddress.getByName(parsed.getHost());

        if (!address.isLoopbackAddress()) throw new IllegalArgumentException("The host must be a loopback address such as 127.0.0.1");

        stop();

        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress(address, parsed.getPort()));

        serverSocket = socket;
        connections = Executors.newVirtualThreadPerTaskExecutor();
        uri = parsed;

        ExecutorService executor = connections;
        Thread acceptor = new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    Socket client = socket.accept();
                    executor.execute(() -> serveConnection(client));
                } catch (SocketException | RejectedExecutionException e) {
                    return; // closed by stop()
                } catch (IOException e) {
                    AkytheraLabs.LOGGER.warn("MCP server failed to accept a connection", e);
                }
            }
        }, "Akythera MCP Server");
        acceptor.setDaemon(true);
        acceptor.start();

        AkytheraLabs.LOGGER.info("MCP server started on {}", parsed);
    }

    public synchronized void stop() {
        if (serverSocket == null) return;

        try {
            serverSocket.close();
        } catch (IOException e) {
            AkytheraLabs.LOGGER.warn("Failed to close the MCP server socket", e);
        }

        connections.shutdownNow();
        AkytheraLabs.LOGGER.info("MCP server stopped on {}", uri);

        serverSocket = null;
        connections = null;
        uri = null;
    }

    public synchronized boolean isRunning() {
        return serverSocket != null;
    }

    public synchronized @Nullable URI getUri() {
        return uri;
    }

    // ***************************************************************
    // HTTP
    // ***************************************************************

    private void serveConnection(final Socket client) {
        try (client) {
            client.setSoTimeout(IDLE_TIMEOUT_MS);

            InputStream in = new BufferedInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();

            while (!client.isClosed()) {
                String requestLine = readLine(in);
                if (requestLine == null) return;
                if (requestLine.isEmpty()) continue;

                String[] parts = requestLine.split(" ");

                if (parts.length != 3) {
                    writeResponse(out, 400, "Bad Request", null, false);
                    return;
                }

                Map<String, String> headers = new HashMap<>();
                int headerBytes = 0;

                for (String line = readLine(in); line != null && !line.isEmpty(); line = readLine(in)) {
                    headerBytes += line.length();

                    if (headerBytes > MAX_HEADER_BYTES) {
                        writeResponse(out, 431, "Request Header Fields Too Large", null, false);
                        return;
                    }

                    int colon = line.indexOf(':');
                    if (colon > 0) headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }

                boolean keepAlive = !"close".equalsIgnoreCase(headers.get("connection"));

                if (headers.containsKey("transfer-encoding")) {
                    writeResponse(out, 411, "Length Required", null, false);
                    return;
                }

                int contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));

                if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                    writeResponse(out, 413, "Content Too Large", null, false);
                    return;
                }

                byte[] body = in.readNBytes(contentLength);
                String method = parts[0];
                String path = URI.create(parts[1]).getPath();
                URI served = getUri();

                if (served == null) return;

                String servedPath = served.getPath() == null || served.getPath().isEmpty() ? "/" : served.getPath();

                String host = headers.getOrDefault("host", "");
                String hostName = host.startsWith("[") ? host.substring(0, host.indexOf(']') + 1) : host.split(":")[0];

                if (!isLoopbackName(hostName) || (headers.containsKey("origin") && !isLoopbackName(URI.create(headers.get("origin")).getHost()))) {
                    writeResponse(out, 403, "Forbidden", null, keepAlive);
                } else if (!servedPath.equals(path)) {
                    writeResponse(out, 404, "Not Found", null, keepAlive);
                } else if (!"POST".equals(method)) {
                    writeResponse(out, 405, "Method Not Allowed", null, keepAlive);
                } else {
                    String response;

                    try {
                        JsonElement message = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));

                        if (message.isJsonArray()) {
                            JsonArray responses = new JsonArray();

                            for (JsonElement element : message.getAsJsonArray()) {
                                JsonObject reply = handleMessage(element);
                                if (reply != null) responses.add(reply);
                            }

                            response = responses.isEmpty() ? null : GSON.toJson(responses);
                        } else {
                            JsonObject reply = handleMessage(message);
                            response = reply == null ? null : GSON.toJson(reply);
                        }
                    } catch (JsonParseException e) {
                        response = GSON.toJson(errorResponse(null, -32700, "Parse error: " + e.getMessage()));
                    } catch (RuntimeException e) {
                        AkytheraLabs.LOGGER.warn("MCP server failed to handle a request", e);
                        response = GSON.toJson(errorResponse(null, -32603, "Internal error: " + e.getMessage()));
                    }

                    if (response == null) {
                        writeResponse(out, 202, "Accepted", null, keepAlive);
                    } else {
                        writeResponse(out, 200, "OK", response, keepAlive);
                    }
                }

                if (!keepAlive) return;
            }
        } catch (SocketTimeoutException | SocketException e) {
            // idle or dropped connection
        } catch (IOException | IllegalArgumentException e) {
            AkytheraLabs.LOGGER.warn("MCP server failed to serve a request", e);
        }
    }

    /// Reads one CRLF-terminated line as ISO-8859-1, null at end of stream.
    private static @Nullable String readLine(final InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();

        for (int b = in.read(); b != -1; b = in.read()) {
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;

                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }

            if (line.size() >= MAX_HEADER_BYTES) throw new IOException("HTTP line too long");

            line.write(b);
        }

        return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
    }

    private static void writeResponse(final OutputStream out, final int status, final String reason, final @Nullable String json, final boolean keepAlive) throws IOException {
        byte[] body = json == null ? new byte[0] : json.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder()
            .append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            .append("Content-Length: ").append(body.length).append("\r\n")
            .append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");

        if (json != null) head.append("Content-Type: application/json; charset=utf-8\r\n");
        if (status == 405) head.append("Allow: POST\r\n");

        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static boolean isLoopbackName(final @Nullable String host) {
        if (host == null) return false;

        String h = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;

        return h.equalsIgnoreCase("localhost") || h.startsWith("127.") || h.equals("::1") || h.equals("0:0:0:0:0:0:0:1");
    }

    // ***************************************************************
    // JSON-RPC
    // ***************************************************************

    private static @Nullable JsonObject handleMessage(final JsonElement element) {
        if (!element.isJsonObject()) return errorResponse(null, -32600, "Invalid request");

        JsonObject message = element.getAsJsonObject();
        JsonElement id = message.get("id");

        if (!message.has("method")) return null; // a response to a request we never send

        String method = message.get("method").getAsString();
        JsonObject params = message.has("params") && message.get("params").isJsonObject() ? message.getAsJsonObject("params") : new JsonObject();

        if (id == null) return null; // notification, e.g. notifications/initialized

        return switch (method) {
            case "initialize" -> {
                String requested = params.has("protocolVersion") ? params.get("protocolVersion").getAsString() : "";
                ModPlatform.ModInfo modInfo = ModPlatformProvider.get().getLoadedModInfo(AkytheraLabs.MODID);

                JsonObject tools = new JsonObject();
                tools.addProperty("listChanged", false);

                JsonObject capabilities = new JsonObject();
                capabilities.add("tools", tools);

                JsonObject serverInfo = new JsonObject();
                serverInfo.addProperty("name", AkytheraLabs.MODID);
                serverInfo.addProperty("title", "Akythera Labs");
                serverInfo.addProperty("version", modInfo == null ? "unknown" : modInfo.versionString());

                JsonObject result = new JsonObject();
                result.addProperty("protocolVersion", PROTOCOL_VERSIONS.contains(requested) ? requested : PROTOCOL_VERSIONS.getFirst());
                result.add("capabilities", capabilities);
                result.add("serverInfo", serverInfo);
                result.addProperty("instructions", "Controls the Akythera Labs editor and the Minecraft client it runs in. "
                    + "Call get_editor_state first. Paths are relative to the open project's root folder. "
                    + "Read and edit node graphs with get_script_graph and edit_script_graph, never by writing the entry's .json "
                    + "file: the editor keeps open entries in memory and overwrites the file on its next save, and wires name "
                    + "pins by index, which only get_script_graph shows. get_script_graph reaches every graph of the top editor "
                    + "screen, closed tabs included.");

                yield resultResponse(id, result);
            }
            case "ping" -> resultResponse(id, new JsonObject());
            case "tools/list" -> {
                JsonArray tools = new JsonArray();
                McpTools.all().forEach(tool -> tools.add(tool.toJson()));

                JsonObject result = new JsonObject();
                result.add("tools", tools);

                yield resultResponse(id, result);
            }
            case "tools/call" -> {
                String name = params.has("name") ? params.get("name").getAsString() : "";
                McpTool tool = McpTools.get(name);

                if (tool == null) yield errorResponse(id, -32602, "Unknown tool: " + name);

                JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject() ? params.getAsJsonObject("arguments") : new JsonObject();
                McpToolResult result;

                try {
                    result = tool.handler().call(arguments);
                } catch (Exception e) {
                    AkytheraLabs.LOGGER.warn("MCP tool {} failed", name, e);
                    result = McpToolResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
                }

                yield resultResponse(id, result.toJson());
            }
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    private static JsonObject resultResponse(final JsonElement id, final JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);

        return response;
    }

    private static JsonObject errorResponse(final @Nullable JsonElement id, final int code, final String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("error", error);

        return response;
    }
}
