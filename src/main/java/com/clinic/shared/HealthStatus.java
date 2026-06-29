package com.clinic.shared;

import java.time.Instant;
import java.util.Map;

public class HealthStatus {

    public final String status;
    public final Map<String, String> checks;
    public final String timestamp;

    public HealthStatus(String status, Map<String, String> checks) {
        this.status = status;
        this.checks = checks;
        this.timestamp = Instant.now().toString();
    }

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
}
