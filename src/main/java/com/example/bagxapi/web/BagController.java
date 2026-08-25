package com.example.bagxapi.web;

import com.example.bagxapi.routing.RoutingContext;
import com.example.bagxapi.routing.RoutingContextHolder;
import com.example.bagxapi.service.BagAggregationService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BagController {

    private final BagAggregationService aggregationService;
    private final String layer;
    private final String version;
    private final String instance;

    public BagController(BagAggregationService aggregationService,
                         @Value("${bag.layer}") String layer,
                         @Value("${bag.version}") String version,
                         @Value("${bag.instance}") String instance) {
        this.aggregationService = aggregationService;
        this.layer = layer;
        this.version = version;
        this.instance = instance;
    }

    @GetMapping("/api/bags")
    public Map<String, Object> getBags() {
        RoutingContext routing = RoutingContextHolder.get();

        Map<String, Object> downstream = aggregationService.callBackend();
        @SuppressWarnings("unchecked")
        Map<String, Object> backendBody = (Map<String, Object>) downstream.getOrDefault("body", Map.of());
        Map<String, Object> orchestrated = aggregationService.orchestrate(backendBody);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("layer", layer);
        body.put("version", version);
        body.put("instance", instance);
        body.put("servedAt", Instant.now().toString());
        body.put("routingContextReceived", routing.asReportedMap());
        body.put("routingContextForwarded", routing.cookieHeaderValue());
        body.put("downstream", downstream);
        body.putAll(orchestrated);
        return body;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("layer", layer);
        body.put("version", version);
        body.put("instance", instance);
        return body;
    }
}
