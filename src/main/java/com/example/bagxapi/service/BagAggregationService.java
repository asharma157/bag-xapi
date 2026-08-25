package com.example.bagxapi.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Calls the backend and assembles the orchestrated bag response.
 *
 * <p>The backend URL is a constant. It is read from configuration only so the app can also be run
 * outside a cluster (docker compose, a laptop); nothing ever appends, rewrites or resolves a
 * version into it. All three bag-service versions live behind this one hostname and the sidecar
 * picks the subset from the propagated {@code bag_service} cookie.</p>
 *
 * <p>Versions of bag-xapi differ in how they orchestrate, not in what the backend returns: 2.3
 * applies a member promotion and quotes a delivery date, 2.2 does not. That is what makes
 * {@code bag_orch} visibly effective on its own.</p>
 */
@Service
public class BagAggregationService {

    private static final Logger log = LoggerFactory.getLogger(BagAggregationService.class);
    private static final String BACKEND_VERSION_HEADER = "x-bag-service-version";

    private final RestTemplate restTemplate;
    private final String backendBaseUrl;
    private final String version;

    public BagAggregationService(RestTemplate bagServiceRestTemplate,
                                 @Value("${bag.downstream.base-url}") String backendBaseUrl,
                                 @Value("${bag.version}") String version) {
        this.restTemplate = bagServiceRestTemplate;
        this.backendBaseUrl = backendBaseUrl;
        this.version = version;
    }

    public Map<String, Object> callBackend() {
        String url = backendBaseUrl + "/api/bags";
        Map<String, Object> downstream = new LinkedHashMap<>();
        downstream.put("layer", "bag-service");
        downstream.put("url", url);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> body = response.getBody() != null ? response.getBody() : Map.of();

            downstream.put("status", "OK");
            // The header is stamped by the pod that actually served the call, so it is the
            // authoritative answer to "which backend version did I just reach?".
            downstream.put("version", response.getHeaders().getFirst(BACKEND_VERSION_HEADER));
            downstream.put("versionReportedInBody", body.get("version"));
            downstream.put("instance", body.get("instance"));
            downstream.put("routingContextReceived", body.get("routingContextReceived"));
            downstream.put("body", body);
            return downstream;
        } catch (Exception ex) {
            log.warn("Call to {} failed: {}", url, ex.toString());
            downstream.put("status", "ERROR");
            downstream.put("version", null);
            downstream.put("error", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            downstream.put("body", Map.of());
            return downstream;
        }
    }

    /**
     * Orchestration applied on top of the backend result. Version-dependent on purpose: pinning
     * only {@code bag_orch} must produce a visible change with an unchanged item list.
     */
    public Map<String, Object> orchestrate(Map<String, Object> backendBody) {
        double subtotal = asDouble(backendBody.get("subtotal"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currency", backendBody.getOrDefault("currency", "USD"));
        result.put("itemCount", backendBody.getOrDefault("itemCount", 0));
        result.put("subtotal", subtotal);

        if (appliesMemberPromotion()) {
            double discount = round(subtotal * 0.10);
            Map<String, Object> promotion = new LinkedHashMap<>();
            promotion.put("code", "MEMBER10");
            promotion.put("description", "10% member discount (added in bag-xapi " + version + ")");
            promotion.put("discount", discount);
            result.put("promotion", promotion);
            result.put("total", round(subtotal - discount));
            result.put("estimatedDelivery", LocalDate.now().plusDays(3).toString());
        } else {
            result.put("promotion", null);
            result.put("total", subtotal);
            result.put("estimatedDelivery", null);
        }

        Object items = backendBody.get("items");
        result.put("items", items instanceof List<?> list ? list : List.of());
        return result;
    }

    private boolean appliesMemberPromotion() {
        return "2.3".equals(version) || version.startsWith("feature");
    }

    private static double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
