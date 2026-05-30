package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.platform.exception.ForbiddenException;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessHoursEnforcerTest {

    @Mock AuditService auditService;
    @Mock SecurityEventRecorder securityEventRecorder;

    @InjectMocks
    AccessHoursEnforcer enforcer;

    private final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(enforcer, "startHour", 8);
        ReflectionTestUtils.setField(enforcer, "endHour", 18);
    }

    private void setHour(int utcHour) {
        // 2026-05-30T{utcHour}:30:00Z
        var fixed = Instant.parse("2026-05-30T" + String.format("%02d", utcHour) + ":30:00Z");
        enforcer.setClock(Clock.fixed(fixed, ZoneOffset.UTC));
    }

    // --- disabled ---

    @Test
    void enforce_whenDisabled_allowsWithoutCheckingTime() {
        ReflectionTestUtils.setField(enforcer, "enabled", false);
        setHour(3); // outside any normal window

        assertThatCode(() -> enforcer.enforce(USER_ID)).doesNotThrowAnyException();
        verifyNoInteractions(auditService, securityEventRecorder);
    }

    // --- normal window (startHour <= endHour) ---

    @Test
    void enforce_withinNormalWindow_allows() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        setHour(12); // 12:30 UTC — inside 08–18

        assertThatCode(() -> enforcer.enforce(USER_ID)).doesNotThrowAnyException();
        verifyNoInteractions(auditService, securityEventRecorder);
    }

    @Test
    void enforce_atExactStartBoundary_allows() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        setHour(8); // exactly startHour

        assertThatCode(() -> enforcer.enforce(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void enforce_atExactEndBoundary_allows() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        setHour(18); // exactly endHour

        assertThatCode(() -> enforcer.enforce(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void enforce_belowNormalWindowStart_denies() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        setHour(6); // before 08:00

        assertThatThrownBy(() -> enforcer.enforce(USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("outside allowed hours");
    }

    @Test
    void enforce_aboveNormalWindowEnd_denies() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        setHour(20); // after 18:00

        assertThatThrownBy(() -> enforcer.enforce(USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("outside allowed hours");
    }

    // --- overnight window (startHour > endHour) ---

    @Test
    void enforce_withinOvernightWindow_atOrAfterStart_allows() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        ReflectionTestUtils.setField(enforcer, "startHour", 22);
        ReflectionTestUtils.setField(enforcer, "endHour", 6);
        setHour(23); // 23:30 — after 22:00 start

        assertThatCode(() -> enforcer.enforce(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void enforce_withinOvernightWindow_atOrBeforeEnd_allows() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        ReflectionTestUtils.setField(enforcer, "startHour", 22);
        ReflectionTestUtils.setField(enforcer, "endHour", 6);
        setHour(5); // 05:30 — before 06:00 end

        assertThatCode(() -> enforcer.enforce(USER_ID)).doesNotThrowAnyException();
    }

    @Test
    void enforce_outsideOvernightWindow_denies() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        ReflectionTestUtils.setField(enforcer, "startHour", 22);
        ReflectionTestUtils.setField(enforcer, "endHour", 6);
        setHour(12); // midday — outside 22–06 window

        assertThatThrownBy(() -> enforcer.enforce(USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("outside allowed hours");
    }

    // --- side effects on denial ---

    @Test
    void enforce_whenDenied_emitsAuditEventAndMetric() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        setHour(3); // outside 08–18

        assertThatThrownBy(() -> enforcer.enforce(USER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(auditService).log(eq(USER_ID), eq("LOGIN_OUTSIDE_ALLOWED_HOURS"),
                eq("User"), eq(USER_ID), isNull(), any(), eq("failure"));
        verify(securityEventRecorder).recordAccessOutsideHours();
    }

    @Test
    @SuppressWarnings("unchecked")
    void enforce_whenDenied_auditContainsHourDetails() {
        ReflectionTestUtils.setField(enforcer, "enabled", true);
        ReflectionTestUtils.setField(enforcer, "startHour", 8);
        ReflectionTestUtils.setField(enforcer, "endHour", 18);
        setHour(3);

        assertThatThrownBy(() -> enforcer.enforce(USER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(auditService).log(any(), any(), any(), any(), isNull(),
                argThat((Map<String, Object> m) ->
                        "3".equals(m.get("utcHour")) &&
                        "8".equals(m.get("allowedStart")) &&
                        "18".equals(m.get("allowedEnd"))),
                any());
    }
}
