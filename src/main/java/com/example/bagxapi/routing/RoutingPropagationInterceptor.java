package com.example.bagxapi.routing;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Replays the inbound routing context onto every outbound call.
 *
 * <p>This is the whole of bag-xapi's routing responsibility, and the gap this POC exists to close:
 * Envoy does not copy application headers from an inbound request onto a new outbound one. Without
 * this interceptor the {@code bag_service} cookie dies at bag-xapi, the sidecar on the outbound leg
 * has nothing to match, and every backend request falls through to the default subset — the chain
 * silently un-pins itself one hop short.</p>
 *
 * <p>Registered on the shared {@code RestTemplate} rather than at each call site, so any future
 * downstream call inherits propagation without anyone having to remember it.</p>
 */
@Component
public class RoutingPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(org.springframework.http.HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        RoutingContext routing = RoutingContextHolder.get();
        HttpHeaders headers = request.getHeaders();

        // Cookie form: what the Istio VirtualService for the next hop matches on
        // (e.g. `regex: ".*bag_service=1\.10.*"` against the cookie header).
        String cookieHeader = routing.cookieHeaderValue();
        if (!cookieHeader.isEmpty()) {
            headers.set(HttpHeaders.COOKIE, cookieHeader);
        }

        // Header form: the same values, for callers and rules that prefer plain headers.
        for (Map.Entry<String, String> entry : routing.cookies().entrySet()) {
            String headerName = RoutingContext.HEADER_FOR_COOKIE.get(entry.getKey());
            if (headerName != null) {
                headers.set(headerName, entry.getValue());
            }
        }

        // Tracing headers, so Kiali can draw the browser-to-backend chain as one trace.
        routing.traceHeaders().forEach(headers::set);

        return execution.execute(request, body);
    }
}
