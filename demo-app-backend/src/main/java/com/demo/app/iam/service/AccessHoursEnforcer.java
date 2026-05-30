package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AC-2(11): denies login attempts that fall outside the configured UTC access window.
 * Overnight windows (startHour > endHour, e.g. 22–06) are supported.
 */
@Service
@RequiredArgsConstructor
public class AccessHoursEnforcer {

    @Value("${app.access.allowed-hours.enabled:false}")
    private boolean enabled;

    @Value("${app.access.allowed-hours.start-hour:0}")
    private int startHour;

    @Value("${app.access.allowed-hours.end-hour:23}")
    private int endHour;

    private final AuditService auditService;
    private final SecurityEventRecorder securityEventRecorder;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock c) { this.clock = c; }

    public void enforce(UUID userId) {
        if (!enabled) return;
        int hour = ZonedDateTime.now(clock).getHour();
        if (!isWithinWindow(hour)) {
            auditService.log(userId, "LOGIN_OUTSIDE_ALLOWED_HOURS", "User", userId, null,
                    Map.of("utcHour", String.valueOf(hour),
                           "allowedStart", String.valueOf(startHour),
                           "allowedEnd", String.valueOf(endHour)),
                    "failure");
            securityEventRecorder.recordAccessOutsideHours();
            throw new ForbiddenException("Access not permitted outside allowed hours");
        }
    }

    private boolean isWithinWindow(int hour) {
        if (startHour <= endHour) {
            // normal window, e.g. 08:00–18:00
            if (hour < startHour) return false;
            return hour <= endHour;
        }
        // overnight window, e.g. 22:00–06:00
        if (hour >= startHour) return true;
        return hour <= endHour;
    }
}
