package com.sursolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/**
 * Client of the SurSolver captcha-solving API. Thread-safe; create one and reuse it.
 *
 * <p>Calls block the current thread. Interrupt the thread to cancel: the call throws
 * {@link InterruptedException}. The API key travels only in request bodies and never appears in
 * exceptions or {@link #toString()}.
 *
 * <pre>{@code
 * SurSolverClient client = SurSolverClient.create("YOUR_API_KEY");
 * SolveResult result = client.solve(Map.of(
 *         "type", TaskTypes.TurnstileTokenProxyLess,
 *         "websiteURL", "https://example.com/login",
 *         "websiteKey", "0x4AAAAAAA...",
 *         "userAgent", USER_AGENT));
 * }</pre>
 */
public final class SurSolverClient {
    /** The production API origin. */
    public static final String DEFAULT_BASE_URL = "https://api.sursolver.com";

    /** Default bound on each HTTP request. */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** Default wait between polls in {@code solve}. */
    public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(3);

    /** Default {@code solve} deadline. */
    public static final Duration DEFAULT_SOLVE_TIMEOUT = Duration.ofSeconds(210);

    /** Default number of resubmissions after {@code ERROR_NO_SLOT_AVAILABLE}. */
    public static final int DEFAULT_MAX_SUBMIT_RETRIES = 1;

    /** Upper bound of the random delay added to a submit retry. */
    public static final Duration SUBMIT_RETRY_JITTER = Duration.ofSeconds(1);

    /** This package's version, sent in the {@code User-Agent} header. */
    public static final String VERSION = loadVersion();

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final String apiKey;
    private final String baseUrl;
    private final Duration requestTimeout;
    private final Duration pollInterval;
    private final Duration solveTimeout;
    private final int maxSubmitRetries;
    private final String appId;
    private final String userAgent;
    private final Transport transport;
    private final SurSolverClock clock;
    private final DoubleSupplier random;

    private SurSolverClient(String apiKey, Builder builder) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ConfigurationException("apiKey must be a non-empty string");
        }
        if (!apiKey.strip().equals(apiKey)) {
            throw new ConfigurationException("apiKey has leading or trailing whitespace");
        }
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        requirePositive("requestTimeout", builder.requestTimeout);
        requirePositive("pollInterval", builder.pollInterval);
        requirePositive("solveTimeout", builder.solveTimeout);
        if (builder.maxSubmitRetries < 0) {
            throw new ConfigurationException("maxSubmitRetries must not be negative");
        }
        String agent = "sursolver-java/" + VERSION;
        if (builder.userAgentSuffix != null) {
            if (builder.userAgentSuffix.isEmpty()
                    || builder.userAgentSuffix.chars().anyMatch(c -> c < 0x20 || c > 0x7e)) {
                throw new ConfigurationException("userAgentSuffix must be non-empty printable ASCII");
            }
            agent += " " + builder.userAgentSuffix;
        }
        this.apiKey = apiKey;
        this.requestTimeout = builder.requestTimeout;
        this.pollInterval = builder.pollInterval;
        this.solveTimeout = builder.solveTimeout;
        this.maxSubmitRetries = builder.maxSubmitRetries;
        this.appId = builder.appId == null || builder.appId.isBlank() ? null : builder.appId.strip();
        this.userAgent = agent;
        this.transport = builder.transport != null ? builder.transport : HttpTransport.shared();
        this.clock = builder.clock != null ? builder.clock : SurSolverClock.system();
        this.random = builder.random != null ? builder.random : () -> ThreadLocalRandom.current().nextDouble();
    }

    /**
     * A client with default options.
     *
     * @param apiKey your API key
     * @return the client
     * @throws ConfigurationException when the key is empty or padded with whitespace
     */
    public static SurSolverClient create(String apiKey) {
        return builder(apiKey).build();
    }

    /**
     * A builder for a client with custom options.
     *
     * @param apiKey your API key
     * @return the builder
     */
    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    /** Options of a {@link SurSolverClient}. Every option has a working default. */
    public static final class Builder {
        private final String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private Duration pollInterval = DEFAULT_POLL_INTERVAL;
        private Duration solveTimeout = DEFAULT_SOLVE_TIMEOUT;
        private int maxSubmitRetries = DEFAULT_MAX_SUBMIT_RETRIES;
        private String appId;
        private String userAgentSuffix;
        private Transport transport;
        private SurSolverClock clock;
        private DoubleSupplier random;

        private Builder(String apiKey) {
            this.apiKey = apiKey;
        }

        /**
         * Sets the API origin.
         *
         * @param baseUrl API origin; plain {@code http://} only for localhost
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Bounds each HTTP request (default 30 s).
         *
         * @param requestTimeout the bound
         * @return this builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * Sets the wait between polls in {@code solve} (default 3 s).
         *
         * @param pollInterval the wait
         * @return this builder
         */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * Sets the {@code solve} deadline (default 210 s).
         *
         * @param solveTimeout the deadline
         * @return this builder
         */
        public Builder solveTimeout(Duration solveTimeout) {
            this.solveTimeout = solveTimeout;
            return this;
        }

        /**
         * Sets how often {@code solve} resubmits after {@code ERROR_NO_SLOT_AVAILABLE} (default 1).
         *
         * @param maxSubmitRetries the number of resubmissions
         * @return this builder
         */
        public Builder maxSubmitRetries(int maxSubmitRetries) {
            this.maxSubmitRetries = maxSubmitRetries;
            return this;
        }

        /**
         * Sends your referral app id with every {@code createTask}.
         *
         * @param appId the app id
         * @return this builder
         */
        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        /**
         * Appends text to the {@code User-Agent} header {@code sursolver-java/<version>}.
         *
         * @param userAgentSuffix printable ASCII
         * @return this builder
         */
        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = userAgentSuffix;
            return this;
        }

        /**
         * Replaces the HTTP transport (proxies, custom TLS, tests).
         *
         * @param transport the transport
         * @return this builder
         */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        /**
         * Replaces the real clock (tests).
         *
         * @param clock the clock
         * @return this builder
         */
        public Builder clock(SurSolverClock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * Replaces the jitter source.
         *
         * @param random returns values in [0, 1)
         * @return this builder
         */
        public Builder random(DoubleSupplier random) {
            this.random = random;
            return this;
        }

        /**
         * Builds the client.
         *
         * @return the client
         * @throws ConfigurationException when an option is unusable; nothing was sent
         */
        public SurSolverClient build() {
            return new SurSolverClient(apiKey, this);
        }
    }

    /** Never reveals the API key. */
    @Override
    public String toString() {
        return "SurSolverClient{baseUrl=" + baseUrl + "}";
    }

    /**
     * Submits a task and returns its id. The task is charged from this point. It is never retried:
     * after a {@link TransportException} the task may exist (check {@link #getBalance()} before
     * submitting again).
     *
     * @param task {@code type} plus that type's fields, sent verbatim. Browser types require {@code userAgent}.
     * @return the task id
     * @throws InterruptedException when the thread is interrupted
     */
    public String createTask(Map<String, ?> task) throws InterruptedException {
        if (task == null) throw new NullPointerException("task");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientKey", apiKey);
        payload.put("task", task);
        if (appId != null) payload.put("appId", appId);
        Map<String, Object> body = solverCall(Operation.CREATE_TASK, "/createTask", payload, null);
        if (body.get("taskId") instanceof String taskId && !taskId.isEmpty()) {
            return taskId;
        }
        throw new ProtocolException(Operation.CREATE_TASK, "errorId 0 without a taskId");
    }

    /**
     * Reads a task's state once. A terminal failure is a {@link TaskFailedException}.
     *
     * @param taskId the task
     * @return the state
     * @throws InterruptedException when the thread is interrupted
     */
    @SuppressWarnings("unchecked")
    public TaskResult getTaskResult(String taskId) throws InterruptedException {
        if (taskId == null) throw new NullPointerException("taskId");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientKey", apiKey);
        payload.put("taskId", taskId);
        Map<String, Object> body = solverCall(Operation.GET_TASK_RESULT, "/getTaskResult", payload, taskId);
        Object status = body.get("status");
        if (TaskResult.PROCESSING.equals(status)) {
            return new TaskResult(TaskResult.PROCESSING, null);
        }
        if (TaskResult.READY.equals(status)) {
            if (body.get("solution") instanceof Map<?, ?> solution) {
                return new TaskResult(TaskResult.READY, (Map<String, Object>) solution);
            }
            throw new ProtocolException(Operation.GET_TASK_RESULT, "status ready without a solution object");
        }
        throw new ProtocolException(Operation.GET_TASK_RESULT, "unknown status " + Json.write(status));
    }

    /**
     * The cash balance in USD as the JSON number the API sends. Display it; do not do money
     * arithmetic on it (the dashboard's {@code GET /v1/balance} has the exact decimal).
     *
     * @return the balance
     * @throws InterruptedException when the thread is interrupted
     */
    public double getBalance() throws InterruptedException {
        Map<String, Object> payload = Map.of("clientKey", apiKey);
        Map<String, Object> body = solverCall(Operation.GET_BALANCE, "/getBalance", payload, null);
        if (body.get("balance") instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw new ProtocolException(Operation.GET_BALANCE, "errorId 0 without a numeric balance");
    }

    /**
     * Creates a task and polls it until it is ready, failed, or the deadline passes, with the client's
     * settings.
     *
     * @param task {@code type} plus that type's fields
     * @return the task id and its solution
     * @throws InterruptedException when the thread is interrupted
     * @see #solve(Map, SolveOptions)
     */
    public SolveResult solve(Map<String, ?> task) throws InterruptedException {
        return solve(task, SolveOptions.defaults());
    }

    /**
     * Creates a task and polls it until it is ready, failed, or the deadline passes.
     *
     * <p>{@code createTask} is resubmitted only after {@code ERROR_NO_SLOT_AVAILABLE} (nothing was
     * created), at most {@code maxSubmitRetries} times; any other submit failure is thrown at once.
     * Polls that fail with a {@link TransportException} or {@code ERROR_INTERNAL_ERROR} are retried until
     * the deadline; any other error ends the call and carries the task id. On the deadline a
     * {@link SolveTimeoutException} is thrown; the task may still finish.
     *
     * @param task {@code type} plus that type's fields
     * @param options per-call overrides and the {@code onTaskCreated} callback
     * @return the task id and its solution
     * @throws InterruptedException when the thread is interrupted
     */
    public SolveResult solve(Map<String, ?> task, SolveOptions options) throws InterruptedException {
        Duration interval = options.pollInterval() != null ? options.pollInterval() : pollInterval;
        Duration timeout = options.solveTimeout() != null ? options.solveTimeout() : solveTimeout;
        requirePositive("pollInterval", interval);
        requirePositive("solveTimeout", timeout);
        if (Thread.interrupted()) throw new InterruptedException();
        Instant deadline = clock.now().plus(timeout);

        String taskId;
        for (int retries = 0; ; retries++) {
            try {
                taskId = createTask(task);
                break;
            } catch (NoSlotAvailableException busy) {
                if (retries >= maxSubmitRetries) throw busy;
                long jitter = (long) Math.floor(random.getAsDouble() * SUBMIT_RETRY_JITTER.toMillis());
                clock.sleep(interval.plusMillis(jitter));
            }
        }
        Consumer<String> onTaskCreated = options.onTaskCreated();
        if (onTaskCreated != null) onTaskCreated.accept(taskId);

        while (true) {
            SurSolverException lastError = null;
            try {
                TaskResult result = getTaskResult(taskId);
                if (result.isReady()) return new SolveResult(taskId, result.solution());
            } catch (TransportException transient_) {
                lastError = transient_;
            } catch (ApiException error) {
                if (!ErrorCodes.ERROR_INTERNAL_ERROR.equals(error.code())) throw error;
                lastError = error;
            }
            clock.sleep(interval);
            if (!clock.now().isBefore(deadline)) {
                throw new SolveTimeoutException(taskId, timeout, lastError);
            }
        }
    }

    /**
     * The public task-type catalog: accepted names, fields, proxy modes and result keys.
     *
     * @return the catalog
     * @throws InterruptedException when the thread is interrupted
     */
    public TaskTypeCatalog listTaskTypes() throws InterruptedException {
        Operation operation = Operation.LIST_TASK_TYPES;
        Map<String, Object> data = catalogCall(operation, "/v1/task-types");
        if (!(data.get("task_types") instanceof List<?> taskTypes) || !(data.get("error_codes") instanceof List<?> errorCodes)) {
            throw new ProtocolException(operation, "data without task_types and error_codes arrays");
        }
        EntryReader read = new EntryReader(operation);
        List<String> codes = read.texts(errorCodes, "error_codes");
        List<TaskTypeInfo> entries = new ArrayList<>();
        for (Object raw : taskTypes) {
            Map<?, ?> entry = read.object(raw);
            entries.add(new TaskTypeInfo(
                    read.optionalText(entry, "page_slug"),
                    read.text(entry, "display_name"),
                    read.text(entry, "task_type"),
                    read.optionalText(entry, "task_type_proxyless"),
                    read.texts(entry.get("aliases"), "aliases"),
                    read.text(entry, "proxy"),
                    read.groups(entry, "required"),
                    read.groups(entry, "any_of"),
                    read.texts(entry.get("optional"), "optional"),
                    read.texts(entry.get("solution_keys"), "solution_keys")));
        }
        return new TaskTypeCatalog(List.copyOf(entries), codes);
    }

    /**
     * Active services with their current USD price per task (decimal strings).
     *
     * @return the services
     * @throws InterruptedException when the thread is interrupted
     */
    public List<ServiceInfo> listServices() throws InterruptedException {
        Operation operation = Operation.LIST_SERVICES;
        Map<String, Object> data = catalogCall(operation, "/v1/services");
        if (!(data.get("services") instanceof List<?> services)) {
            throw new ProtocolException(operation, "data without a services array");
        }
        EntryReader read = new EntryReader(operation);
        List<ServiceInfo> out = new ArrayList<>();
        for (Object raw : services) {
            Map<?, ?> entry = read.object(raw);
            boolean noLatency = entry.containsKey("avg_latency_ms") && entry.get("avg_latency_ms") == null;
            out.add(new ServiceInfo(
                    read.integer(entry, "id"),
                    read.text(entry, "slug"),
                    read.text(entry, "display_name"),
                    read.text(entry, "price_per_request"),
                    read.text(entry, "status"),
                    noLatency ? null : read.integer(entry, "avg_latency_ms")));
        }
        return List.copyOf(out);
    }

    private static String loadVersion() {
        try (InputStream in = SurSolverClient.class.getResourceAsStream("version.properties")) {
            Properties properties = new Properties();
            if (in != null) properties.load(in);
            return properties.getProperty("version", "unknown");
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void requirePositive(String name, Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new ConfigurationException(name + " must be positive");
        }
    }

    private static String normalizeBaseUrl(String raw) {
        URI uri;
        try {
            uri = raw == null ? null : new URI(raw);
        } catch (URISyntaxException error) {
            uri = null;
        }
        if (uri == null || !uri.isAbsolute() || uri.getHost() == null) {
            throw new ConfigurationException("baseUrl is not a valid absolute URL");
        }
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        boolean loopbackHttp = scheme.equals("http") && LOOPBACK_HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT));
        if (!scheme.equals("https") && !loopbackHttp) {
            throw new ConfigurationException("baseUrl must use https:// (plain http:// only for localhost)");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || raw.contains("?") || raw.contains("#")) {
            throw new ConfigurationException("baseUrl must not carry credentials, a query or a fragment");
        }
        String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/+$", "");
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return scheme + "://" + uri.getHost() + port + path;
    }

    private Transport.Request request(String method, String path, byte[] body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("User-Agent", userAgent);
        if (body != null) headers.put("Content-Type", "application/json");
        return new Transport.Request(method, baseUrl + path, Map.copyOf(headers), body, requestTimeout);
    }

    private Transport.Response send(Operation operation, Transport.Request request) throws InterruptedException {
        Transport.Response response;
        try {
            response = transport.send(request);
        } catch (IOException | UncheckedIOException error) {
            String reason = error instanceof HttpTimeoutException
                    ? "request timed out after " + request.timeout().toMillis() + " ms"
                    : "connection failed";
            throw new TransportException(operation, reason, null, null, null, error);
        }
        if (response.status() == 200) {
            return response;
        }
        String serverCode = null;
        Integer retryAfter = null;
        if (parse(response.body()) instanceof Map<?, ?> envelope && envelope.get("error") instanceof Map<?, ?> detail) {
            if (detail.get("code") instanceof String code) serverCode = code;
            if (detail.get("retry_after_seconds") instanceof Number seconds) retryAfter = seconds.intValue();
        }
        String reason = "HTTP " + response.status() + (serverCode == null ? "" : " (" + serverCode + ")");
        throw new TransportException(operation, reason, response.status(), serverCode, retryAfter, null);
    }

    /** POSTs to a solver endpoint; returns the errorId 0 body or throws the classified error. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> solverCall(Operation operation, String path, Map<String, Object> payload, String taskId)
            throws InterruptedException {
        byte[] encoded = Json.write(payload).getBytes(StandardCharsets.UTF_8);
        Transport.Response response = send(operation, request("POST", path, encoded));
        Object parsed = parse(response.body());
        if (!(parsed instanceof Map<?, ?> map) || !(map.get("errorId") instanceof Number errorIdNumber)
                || errorIdNumber.doubleValue() != Math.rint(errorIdNumber.doubleValue())) {
            String what = parsed == NOT_JSON ? "not JSON" : "not the solver envelope";
            throw new TransportException(operation, "HTTP 200 body is " + what, 200, null, null, null);
        }
        Map<String, Object> body = (Map<String, Object>) map;
        double errorId = errorIdNumber.doubleValue();
        if (errorId == 0) {
            return body;
        }
        if (errorId != 1) {
            throw new ProtocolException(operation, "errorId " + Json.write(errorIdNumber));
        }
        if (!(body.get("errorCode") instanceof String code)) {
            throw new ProtocolException(operation, "errorId 1 without a string errorCode");
        }
        String description = body.get("errorDescription") instanceof String text ? text : "";
        List<String> supported = new ArrayList<>();
        if (body.get("supportedUserAgents") instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String ua) supported.add(ua);
            }
        }
        throw ApiException.create(operation, code, description, taskId, supported);
    }

    /** GETs a public catalog endpoint and returns its {@code data} object. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> catalogCall(Operation operation, String path) throws InterruptedException {
        Transport.Response response = send(operation, request("GET", path, null));
        if (parse(response.body()) instanceof Map<?, ?> envelope && Boolean.TRUE.equals(envelope.get("success"))
                && envelope.get("data") instanceof Map<?, ?> data) {
            return (Map<String, Object>) data;
        }
        throw new TransportException(operation, "HTTP 200 body is not the API envelope", 200, null, null, null);
    }

    private static final Object NOT_JSON = new Object();

    /** The parsed body, or {@link #NOT_JSON}. */
    private static Object parse(byte[] body) {
        try {
            return Json.parse(new String(body, StandardCharsets.UTF_8));
        } catch (Json.ParseException error) {
            return NOT_JSON;
        }
    }

    /** Typed reads of catalog entries; a missing or mistyped field is a ProtocolException. */
    private static final class EntryReader {
        private final Operation operation;

        EntryReader(Operation operation) {
            this.operation = operation;
        }

        private ProtocolException fail(String key, String expected) {
            return new ProtocolException(operation, "catalog field " + key + " is not " + expected);
        }

        Map<?, ?> object(Object raw) {
            if (raw instanceof Map<?, ?> map) return map;
            throw new ProtocolException(operation, "a catalog entry is not an object");
        }

        String text(Map<?, ?> entry, String key) {
            if (entry.get(key) instanceof String text) return text;
            throw fail(key, "a string");
        }

        String optionalText(Map<?, ?> entry, String key) {
            Object value = entry.get(key);
            if (value == null || value instanceof String) return (String) value;
            throw fail(key, "a string or null");
        }

        long integer(Map<?, ?> entry, String key) {
            if (entry.get(key) instanceof Long number) return number;
            throw fail(key, "an integer");
        }

        List<String> texts(Object raw, String key) {
            if (raw instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object entry : list) {
                    if (!(entry instanceof String text)) throw fail(key, "a list of strings");
                    out.add(text);
                }
                return List.copyOf(out);
            }
            throw fail(key, "a list of strings");
        }

        List<List<String>> groups(Map<?, ?> entry, String key) {
            if (entry.get(key) instanceof List<?> list) {
                List<List<String>> out = new ArrayList<>();
                for (Object group : list) {
                    if (!(group instanceof List<?>)) throw fail(key, "a list of string lists");
                    try {
                        out.add(texts(group, key));
                    } catch (ProtocolException notStrings) {
                        throw fail(key, "a list of string lists");
                    }
                }
                return List.copyOf(out);
            }
            throw fail(key, "a list of string lists");
        }
    }
}
