package com.example.bagxapi.routing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The routing context carried by a single request.
 *
 * <p>It is deliberately dumb: it holds the three routing cookie values and the tracing headers,
 * and knows how to render them back onto an outbound call. No layer ever interprets a value to
 * make a routing decision — the Istio sidecar does that. The application's only job is to make
 * sure the values survive the hop.</p>
 */
public final class RoutingContext {

    /** Pins the version of bag-ui. */
    public static final String COOKIE_FED = "bag_fed";
    /** Pins the version of bag-xapi. */
    public static final String COOKIE_ORCH = "bag_orch";
    /** Pins the version of bag-service. */
    public static final String COOKIE_SERVICE = "bag_service";

    /** Every routing cookie, in a stable order. */
    public static final List<String> ROUTING_COOKIES = List.of(COOKIE_FED, COOKIE_ORCH, COOKIE_SERVICE);

    /**
     * Header form of each routing cookie. A VirtualService can match either form; carrying both
     * means a non-browser client (curl, a test harness, another service) can pin a version without
     * having to synthesise a Cookie header.
     */
    public static final Map<String, String> HEADER_FOR_COOKIE = Map.of(
            COOKIE_FED, "x-bag-fed",
            COOKIE_ORCH, "x-bag-orch",
            COOKIE_SERVICE, "x-bag-service");

    /**
     * Istio needs these propagated to stitch a trace together; without them Kiali shows
     * disconnected single-hop graphs instead of the browser-to-backend chain.
     */
    public static final List<String> TRACE_HEADERS = List.of(
            "x-request-id",
            "x-b3-traceid",
            "x-b3-spanid",
            "x-b3-parentspanid",
            "x-b3-sampled",
            "x-b3-flags",
            "b3",
            "traceparent",
            "tracestate",
            "x-ot-span-context");

    private static final RoutingContext EMPTY = new RoutingContext(Map.of(), Map.of());

    private final Map<String, String> cookies;
    private final Map<String, String> traceHeaders;

    RoutingContext(Map<String, String> cookies, Map<String, String> traceHeaders) {
        this.cookies = Collections.unmodifiableMap(new LinkedHashMap<>(cookies));
        this.traceHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(traceHeaders));
    }

    public static RoutingContext empty() {
        return EMPTY;
    }

    /** Routing cookie values that were actually present on the inbound request. */
    public Map<String, String> cookies() {
        return cookies;
    }

    public Map<String, String> traceHeaders() {
        return traceHeaders;
    }

    public String value(String cookieName) {
        return cookies.get(cookieName);
    }

    /**
     * Rebuilds a {@code Cookie} header containing only the routing cookies. Only these three are
     * forwarded: everything else on the browser's cookie jar (sessions, analytics, consent) stays
     * where it was and is never leaked to an internal service.
     */
    public String cookieHeaderValue() {
        StringBuilder sb = new StringBuilder();
        for (String name : ROUTING_COOKIES) {
            String value = cookies.get(name);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(value);
        }
        return sb.toString();
    }

    /** Null-safe view of all three cookies, used to prove propagation in API responses. */
    public Map<String, String> asReportedMap() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : ROUTING_COOKIES) {
            out.put(name, cookies.get(name));
        }
        return out;
    }

    public boolean isEmpty() {
        return cookies.isEmpty();
    }
}
