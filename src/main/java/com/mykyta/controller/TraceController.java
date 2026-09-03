package com.mykyta.controller;

import com.mykyta.response.TraceDetailsResponse;
import com.mykyta.service.TraceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes completed execution traces separately from latency-sensitive chat responses. */
@RestController
@RequestMapping("/api/traces")
public class TraceController {
    private final TraceService traceService;

    /**
     * Creates the lazy trace endpoint.
     * @param traceService API mapping and trace lookup service
     */
    public TraceController(TraceService traceService) { this.traceService = traceService; }

    /**
     * Retrieves one trace only when a user opens execution details.
     * @param traceId identifier returned in the assistant response
     * @return complete flat span set with causal parent identifiers
     */
    @GetMapping("/{traceId}")
    public TraceDetailsResponse get(@PathVariable String traceId) { return traceService.get(traceId); }
}
