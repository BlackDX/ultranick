package net.ultranick.common.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.ultranick.api.UltraNickAPI;
import net.ultranick.api.model.DisguiseProfile;
import net.ultranick.api.model.NickResult;
import net.ultranick.api.model.SkinData;
import net.ultranick.common.json.UltraJson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Built-in lightweight, high-performance REST API Server for UltraNick.
 * Zero external web framework dependencies (uses standard JDK HttpServer).
 *
 * @author Chatbxn
 */
public final class UltraNickHttpServer implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger("UltraNick-HTTP");

    private final String bindHost;
    private final int port;
    private final String authToken;
    private final UltraNickAPI api;
    private HttpServer server;

    public UltraNickHttpServer(@NotNull String bindHost, int port, @Nullable String authToken, @NotNull UltraNickAPI api) {
        this.bindHost = (bindHost != null && !bindHost.isBlank()) ? bindHost : "0.0.0.0";
        this.port = port > 0 ? port : 8085;
        this.authToken = (authToken != null && !authToken.isBlank()) ? authToken : null;
        this.api = Objects.requireNonNull(api, "api cannot be null");
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindHost, port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        // Register API Routes
        server.createContext("/api/v1/health", new HealthHandler());
        server.createContext("/api/v1/status", new HealthHandler());
        server.createContext("/api/v1/nicks", new NicksListHandler());
        server.createContext("/api/v1/nick", new NickPlayerHandler());
        server.createContext("/api/v1/realname", new RealNameHandler());
        server.createContext("/api/v1/skins", new SkinsHandler());

        server.start();
        LOGGER.info("UltraNick REST API Server listening on " + bindHost + ":" + port);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (authToken == null || authToken.isEmpty()) {
            return true; // No auth required if not configured
        }
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return authToken.equals(token);
        }
        String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        return authToken.equals(apiKey);
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = (data instanceof String str) ? str : UltraJson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", true);
        obj.addProperty("status", statusCode);
        obj.addProperty("message", message);
        sendJsonResponse(exchange, statusCode, obj);
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    // ==========================================
    // HTTP Handlers
    // ==========================================

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            JsonObject res = new JsonObject();
            res.addProperty("status", "UP");
            res.addProperty("plugin", "UltraNick");
            res.addProperty("author", "Chatbxn");
            res.addProperty("activeDisguises", api.getActiveDisguises().size());
            res.addProperty("timestamp", System.currentTimeMillis());
            sendJsonResponse(exchange, 200, res);
        }
    }

    private final class NicksListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }
            Collection<DisguiseProfile> disguises = api.getActiveDisguises();
            sendJsonResponse(exchange, 200, disguises);
        }
    }

    private final class NickPlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            String path = exchange.getRequestURI().getPath(); // /api/v1/nick/{target}
            String target = path.replaceFirst("^/api/v1/nick/?", "").trim();

            if (target.isEmpty()) {
                sendError(exchange, 400, "Missing player UUID or name parameter.");
                return;
            }

            UUID playerUuid = resolveUuid(target);
            if (playerUuid == null) {
                sendError(exchange, 404, "Player not found or invalid UUID: " + target);
                return;
            }

            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            switch (method) {
                case "GET": {
                    Optional<DisguiseProfile> opt = api.getDisguise(playerUuid);
                    if (opt.isPresent()) {
                        sendJsonResponse(exchange, 200, opt.get());
                    } else {
                        JsonObject res = new JsonObject();
                        res.addProperty("disguised", false);
                        res.addProperty("uuid", playerUuid.toString());
                        sendJsonResponse(exchange, 200, res);
                    }
                    break;
                }
                case "POST": {
                    String body = readRequestBody(exchange);
                    String nickName = null;
                    String skinName = null;
                    String rank = null;

                    if (!body.isBlank()) {
                        try {
                            JsonObject json = UltraJson.fromJson(body, JsonObject.class);
                            if (json.has("nickName")) nickName = json.get("nickName").getAsString();
                            if (json.has("skin")) skinName = json.get("skin").getAsString();
                            if (json.has("rank")) rank = json.get("rank").getAsString();
                        } catch (Exception ignored) {}
                    }

                    if (nickName == null || nickName.isBlank()) {
                        api.disguiseRandom(playerUuid).thenAccept(result -> {
                            try {
                                JsonObject res = new JsonObject();
                                res.addProperty("success", result.isSuccessful());
                                res.addProperty("result", result.name());
                                res.addProperty("message", result.getDefaultMessage());
                                sendJsonResponse(exchange, result.isSuccessful() ? 200 : 400, res);
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Error writing HTTP response", e);
                            }
                        });
                    } else {
                        SkinData skin = skinName != null ? api.getSkin(skinName).orElse(null) : null;
                        api.disguise(playerUuid, nickName, skin, rank).thenAccept(result -> {
                            try {
                                JsonObject res = new JsonObject();
                                res.addProperty("success", result.isSuccessful());
                                res.addProperty("result", result.name());
                                res.addProperty("message", result.getDefaultMessage());
                                sendJsonResponse(exchange, result.isSuccessful() ? 200 : 400, res);
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Error writing HTTP response", e);
                            }
                        });
                    }
                    break;
                }
                case "DELETE": {
                    api.undisguise(playerUuid).thenAccept(result -> {
                        try {
                            JsonObject res = new JsonObject();
                            res.addProperty("success", result.isSuccessful());
                            res.addProperty("result", result.name());
                            res.addProperty("message", result.getDefaultMessage());
                            sendJsonResponse(exchange, result.isSuccessful() ? 200 : 400, res);
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "Error writing HTTP response", e);
                        }
                    });
                    break;
                }
                default:
                    sendError(exchange, 405, "Method Not Allowed");
            }
        }
    }

    private final class RealNameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String name = path.replaceFirst("^/api/v1/realname/?", "").trim();

            if (name.isEmpty()) {
                sendError(exchange, 400, "Missing disguised name parameter");
                return;
            }

            Optional<String> realName = api.getRealName(name);
            Optional<UUID> realUuid = api.getRealUUID(name);

            if (realName.isPresent() && realUuid.isPresent()) {
                JsonObject res = new JsonObject();
                res.addProperty("disguisedName", name);
                res.addProperty("realName", realName.get());
                res.addProperty("realUuid", realUuid.get().toString());
                sendJsonResponse(exchange, 200, res);
            } else {
                sendError(exchange, 404, "No active disguise found for name: " + name);
            }
        }
    }

    private final class SkinsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 204, "");
                return;
            }
            if (!isAuthorized(exchange)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }
            JsonObject res = new JsonObject();
            res.add("skins", UltraJson.gson().toJsonTree(Arrays.asList("Steve", "Alex", "Ari", "Efe", "Kai", "Makena", "Noor", "Sunny", "Zuri")));
            sendJsonResponse(exchange, 200, res);
        }
    }

    private UUID resolveUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return api.getRealUUID(input).orElse(null);
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("UltraNick REST API Server stopped.");
        }
    }
}
