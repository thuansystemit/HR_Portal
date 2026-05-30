package com.demo.app.iam.service;

import com.demo.app.compliance.service.AuditService;
import com.demo.app.config.JwtConfig;
import com.demo.app.iam.repository.UserRepository;
import com.demo.app.platform.metrics.SecurityEventRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * IA-4(e): Automatically deactivate accounts that have been inactive for the configured
 * number of days (NIST 800-53 default: 90 days). Runs daily at 03:00 UTC.
 *
 * "Inactive" means no successful login since the cutoff. Accounts that were provisioned
 * but never logged in are also caught once their creation date passes the cutoff — this
 * prevents dormant provisioned accounts from remaining active indefinitely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InactiveAccountService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final SecurityEventRecorder securityEventRecorder;
    private final SessionActivityService sessionActivityService;
    private final TokenDenylistService tokenDenylistService;
    private final JwtConfig jwtConfig;

    @Value("${app.account.inactivity.deactivate-after-days:90}")
    private int inactivityDays = 90;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void deactivateInactiveAccounts() {
        var cutoff = Instant.now().minusSeconds((long) inactivityDays * 86_400);
        var inactive = userRepository.findActiveUsersInactiveSince(cutoff);
        if (inactive.isEmpty()) {
            log.debug("IA-4(e): no inactive accounts to deactivate (cutoff={})", cutoff);
            return;
        }
        log.info("IA-4(e): deactivating {} account(s) inactive since {}", inactive.size(), cutoff);
        for (var user : inactive) {
            user.setStatus("inactive");
            userRepository.save(user);
            revokeAllSessions(user.getId());
            auditService.log(null, "USER_AUTO_DEACTIVATED", "User", user.getId(),
                    Map.of("status", "active"),
                    Map.of("status", "inactive", "reason", "inactivity",
                           "cutoffDays", String.valueOf(inactivityDays)),
                    "success");
            securityEventRecorder.recordUserAutoDeactivated();
        }
        log.info("IA-4(e): deactivated {} inactive account(s)", inactive.size());
    }

    private void revokeAllSessions(UUID userId) {
        var jtis = sessionActivityService.getSessionJtis(userId);
        long maxTtl = jwtConfig.getAccessExpirySeconds();
        jtis.forEach(jti -> {
            tokenDenylistService.deny(jti, maxTtl);
            sessionActivityService.remove(jti);
        });
        sessionActivityService.clearUserSessions(userId);
        if (!jtis.isEmpty()) {
            securityEventRecorder.recordUserSessionsRevoked(jtis.size());
        }
    }
}
