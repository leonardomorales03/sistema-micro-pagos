package com.micropay.backend.api.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Tag(name = "System", description = "Health/info endpoints (proxy Nginx /healthz → este)")
@RestController
@RequestMapping({"/api/v1/system", "/api"})
public class SystemController {

    private final Environment env;
    private final Optional<BuildProperties> build;

    public SystemController(Environment env, Optional<BuildProperties> build) {
        this.env = env;
        this.build = build;
    }

    @GetMapping(value = {"/healthz", "/health"})
    @Operation(summary = "Health check simple (sin details — para Kubernetes liveness/readiness)")
    @ApiResponse(responseCode = "200", description = "Servicio UP")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "micropay-backend");
        body.put("profiles", Arrays.toString(env.getActiveProfiles()));
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/info")
    @Operation(summary = "Información build + properties (Spring Actuator build info integration)")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", env.getProperty("spring.application.name", "sistema-micro-pagos-backend"));
        app.put("profiles", env.getActiveProfiles());
        body.put("app", app);
        build.ifPresent(bp -> {
            Map<String, Object> bi = new LinkedHashMap<>();
            bi.put("group", bp.getGroup());
            bi.put("artifact", bp.getArtifact());
            bi.put("name", bp.getName());
            bi.put("version", bp.getVersion());
            bi.put("time", bp.getTime() != null ? bp.getTime().toString() : null);
            body.put("build", bi);
        });
        return ResponseEntity.ok(body);
    }
}
