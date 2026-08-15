package net.ultranick.common.redis;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ultra-lightweight, high-performance native Redis RESP protocol client.
 * Zero external library dependencies, built-in connection pool and dedicated PubSub engine.
 *
 * @author Chatbxn
 */
public final class UltraRedisClient implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger("UltraNick-Redis");

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int timeoutMs;
    private final BlockingQueue<RedisConnection> connectionPool;
    private final int poolSize;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // PubSub State
    private final Map<String, Set<Consumer<String>>> channelSubscribers = new ConcurrentHashMap<>();
    private Thread pubSubThread;
    private volatile Socket pubSubSocket;
    private final AtomicBoolean pubSubRunning = new AtomicBoolean(false);

    public UltraRedisClient(String host, int port, String password, int database, int timeoutMs, int poolSize) {
        this.host = (host != null && !host.isBlank()) ? host : "127.0.0.1";
        this.port = port > 0 ? port : 6379;
        this.password = (password != null && !password.isBlank()) ? password : null;
        this.database = Math.max(0, database);
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 3000;
        this.poolSize = Math.max(2, poolSize);
        this.connectionPool = new ArrayBlockingQueue<>(this.poolSize);

        initializePool();
    }

    private void initializePool() {
        for (int i = 0; i < poolSize; i++) {
            try {
                RedisConnection conn = createConnection();
                connectionPool.offer(conn);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Initial Redis connection failed (will retry on demand): " + e.getMessage());
            }
        }
    }

    private RedisConnection createConnection() throws IOException {
        Socket socket = new Socket();
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);

        RedisConnection connection = new RedisConnection(socket);

        if (password != null) {
            String res = connection.sendCommand("AUTH", password);
            if (res != null && res.startsWith("ERR")) {
                LOGGER.warning("Redis AUTH returned error: " + res);
            }
        }

        if (database > 0) {
            connection.sendCommand("SELECT", String.valueOf(database));
        }

        return connection;
    }

    private RedisConnection borrowConnection() throws Exception {
        if (closed.get()) {
            throw new IllegalStateException("Redis client is closed.");
        }

        RedisConnection conn = connectionPool.poll(500, TimeUnit.MILLISECONDS);
        if (conn == null || !conn.isValid()) {
            if (conn != null) {
                conn.close();
            }
            conn = createConnection();
        }
        return conn;
    }

    private void returnConnection(RedisConnection connection) {
        if (connection == null) return;
        if (closed.get() || !connection.isValid()) {
            connection.close();
            return;
        }
        if (!connectionPool.offer(connection)) {
            connection.close();
        }
    }

    /**
     * Executes a Redis command synchronously using a pooled connection.
     */
    public synchronized String execute(String... args) {
        RedisConnection conn = null;
        try {
            conn = borrowConnection();
            return conn.sendCommand(args);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Redis execute command failed: " + Arrays.toString(args) + " (" + e.getMessage() + ")");
            return null;
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * String GET
     */
    public String get(String key) {
        return execute("GET", key);
    }

    /**
     * String SET
     */
    public boolean set(String key, String value) {
        String res = execute("SET", key, value);
        return "OK".equalsIgnoreCase(res);
    }

    /**
     * String SET with Expiration in seconds
     */
    public boolean setEx(String key, int seconds, String value) {
        String res = execute("SETEX", key, String.valueOf(seconds), value);
        return "OK".equalsIgnoreCase(res);
    }

    /**
     * Key DEL
     */
    public boolean del(String key) {
        String res = execute("DEL", key);
        return res != null && !res.equals("0");
    }

    /**
     * Key EXISTS
     */
    public boolean exists(String key) {
        String res = execute("EXISTS", key);
        return "1".equals(res);
    }

    /**
     * Hash HSET
     */
    public boolean hset(String key, String field, String value) {
        String res = execute("HSET", key, field, value);
        return res != null;
    }

    /**
     * Hash HGET
     */
    public String hget(String key, String field) {
        return execute("HGET", key, field);
    }

    /**
     * Hash HDEL
     */
    public boolean hdel(String key, String field) {
        String res = execute("HDEL", key, field);
        return res != null && !res.equals("0");
    }

    /**
     * Keys lookup
     */
    public Set<String> keys(String pattern) {
        RedisConnection conn = null;
        try {
            conn = borrowConnection();
            List<String> list = conn.sendArrayCommand("KEYS", pattern);
            return list != null ? new HashSet<>(list) : Collections.emptySet();
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Redis KEYS error: " + e.getMessage());
            return Collections.emptySet();
        } finally {
            returnConnection(conn);
        }
    }

    /**
     * PubSub Publish
     */
    public long publish(String channel, String message) {
        String res = execute("PUBLISH", channel, message);
        try {
            return res != null ? Long.parseLong(res) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * PubSub Subscribe
     */
    public synchronized void subscribe(String channel, Consumer<String> listener) {
        channelSubscribers.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(listener);
        ensurePubSubThreadRunning();
        // Send subscribe command to active pubsub socket
        sendPubSubSubscribe(channel);
    }

    private synchronized void sendPubSubSubscribe(String channel) {
        if (pubSubSocket != null && !pubSubSocket.isClosed()) {
            try {
                OutputStream out = pubSubSocket.getOutputStream();
                writeRespArray(out, "SUBSCRIBE", channel);
                out.flush();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to send SUBSCRIBE command: " + e.getMessage());
            }
        }
    }

    private synchronized void ensurePubSubThreadRunning() {
        if (pubSubRunning.compareAndSet(false, true)) {
            pubSubThread = new Thread(this::runPubSubLoop, "UltraNick-Redis-PubSub");
            pubSubThread.setDaemon(true);
            pubSubThread.start();
        }
    }

    private void runPubSubLoop() {
        while (!closed.get() && pubSubRunning.get()) {
            try {
                pubSubSocket = new Socket();
                pubSubSocket.setKeepAlive(true);
                pubSubSocket.setTcpNoDelay(true);
                pubSubSocket.connect(new InetSocketAddress(host, port), timeoutMs);
                pubSubSocket.setSoTimeout(0); // infinite timeout for subscriber

                InputStream in = new BufferedInputStream(pubSubSocket.getInputStream());
                OutputStream out = new BufferedOutputStream(pubSubSocket.getOutputStream());

                if (password != null) {
                    writeRespArray(out, "AUTH", password);
                    out.flush();
                    readRespObject(in); // Read auth response
                }

                // Re-subscribe all active channels
                for (String channel : channelSubscribers.keySet()) {
                    writeRespArray(out, "SUBSCRIBE", channel);
                }
                out.flush();

                LOGGER.info("Redis PubSub listener connected to " + host + ":" + port);

                // Read continuous pubsub events
                while (!closed.get() && !pubSubSocket.isClosed()) {
                    Object resp = readRespObject(in);
                    if (resp instanceof List<?> list) {
                        if (list.size() >= 3) {
                            String type = Objects.toString(list.get(0), "");
                            if ("message".equalsIgnoreCase(type)) {
                                String channel = Objects.toString(list.get(1), "");
                                String payload = Objects.toString(list.get(2), "");
                                Set<Consumer<String>> listeners = channelSubscribers.get(channel);
                                if (listeners != null) {
                                    for (Consumer<String> consumer : listeners) {
                                        try {
                                            consumer.accept(payload);
                                        } catch (Throwable t) {
                                            LOGGER.log(Level.WARNING, "Error processing PubSub message on channel " + channel, t);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (!closed.get()) {
                    LOGGER.log(Level.FINE, "Redis PubSub connection dropped, reconnecting in 3 seconds: " + e.getMessage());
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            } finally {
                if (pubSubSocket != null) {
                    try {
                        pubSubSocket.close();
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    public boolean ping() {
        String res = execute("PING");
        return "PONG".equalsIgnoreCase(res);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            pubSubRunning.set(false);
            if (pubSubSocket != null) {
                try {
                    pubSubSocket.close();
                } catch (IOException ignored) {}
            }
            if (pubSubThread != null) {
                pubSubThread.interrupt();
            }
            RedisConnection conn;
            while ((conn = connectionPool.poll()) != null) {
                conn.close();
            }
        }
    }

    // ==========================================
    // RESP Protocol Helper Methods
    // ==========================================

    private static void writeRespArray(OutputStream out, String... args) throws IOException {
        out.write(('*' + String.valueOf(args.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String arg : args) {
            byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
            out.write(('$' + String.valueOf(bytes.length) + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Object readRespObject(InputStream in) throws IOException {
        int prefix = in.read();
        if (prefix == -1) {
            throw new EOFException("Connection closed by Redis server.");
        }

        switch (prefix) {
            case '+': // Simple String
                return readLine(in);
            case '-': // Error
                return "ERR " + readLine(in);
            case ':': // Integer
                return Long.parseLong(readLine(in));
            case '$': { // Bulk String
                int len = Integer.parseInt(readLine(in));
                if (len == -1) {
                    return null;
                }
                byte[] data = new byte[len];
                int read = 0;
                while (read < len) {
                    int r = in.read(data, read, len - read);
                    if (r == -1) throw new EOFException("Premature EOF in bulk string");
                    read += r;
                }
                // Read trailing CRLF
                in.read(); // \r
                in.read(); // \n
                return new String(data, StandardCharsets.UTF_8);
            }
            case '*': { // Array
                int count = Integer.parseInt(readLine(in));
                if (count == -1) {
                    return null;
                }
                List<Object> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(readRespObject(in));
                }
                return list;
            }
            default:
                throw new IOException("Unknown RESP prefix: " + (char) prefix);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                int next = in.read();
                if (next == '\n') {
                    break;
                }
                buffer.write(b);
                if (next != -1) buffer.write(next);
            } else {
                buffer.write(b);
            }
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * Inner managed Redis socket connection.
     */
    private static final class RedisConnection implements AutoCloseable {
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;

        public RedisConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new BufferedInputStream(socket.getInputStream());
            this.out = new BufferedOutputStream(socket.getOutputStream());
        }

        public String sendCommand(String... args) throws IOException {
            writeRespArray(out, args);
            out.flush();
            Object resp = readRespObject(in);
            if (resp == null) return null;
            if (resp instanceof String str) return str;
            if (resp instanceof Long num) return num.toString();
            return resp.toString();
        }

        public List<String> sendArrayCommand(String... args) throws IOException {
            writeRespArray(out, args);
            out.flush();
            Object resp = readRespObject(in);
            if (resp instanceof List<?> list) {
                List<String> res = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item != null) res.add(item.toString());
                }
                return res;
            }
            return Collections.emptyList();
        }

        public boolean isValid() {
            return socket != null && socket.isConnected() && !socket.isClosed() && !socket.isInputShutdown() && !socket.isOutputShutdown();
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}
