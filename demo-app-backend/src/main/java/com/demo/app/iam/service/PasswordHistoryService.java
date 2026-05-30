package com.demo.app.iam.service;

import com.demo.app.iam.entity.PasswordHistory;
import com.demo.app.iam.repository.PasswordHistoryRepository;
import com.demo.app.platform.exception.PasswordPolicyException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * IA-5(1)(h): Verifiers SHALL maintain a history of previously used passwords
 * and disallow their reuse (NIST SP 800-63B 5.1.1.2).
 */
@Service
@RequiredArgsConstructor
public class PasswordHistoryService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.password.history.count:5}")
    private int historyCount = 5;

    /**
     * Throws {@link PasswordPolicyException} if {@code rawPassword} matches any of the
     * last {@code historyCount} stored hashes for the given user.
     */
    public void checkNotReused(UUID userId, String rawPassword) {
        var history = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        boolean reused = history.stream()
                .limit(historyCount)
                .anyMatch(h -> passwordEncoder.matches(rawPassword, h.getPasswordHash()));
        if (reused) {
            throw new PasswordPolicyException(List.of(
                    "Password was recently used — choose a password not used in your last "
                            + historyCount + " changes"));
        }
    }

    /**
     * Persists {@code encodedHash} as a new history entry and prunes entries beyond
     * {@code historyCount} so the table stays bounded.
     */
    @Transactional
    public void record(UUID userId, String encodedHash) {
        passwordHistoryRepository.save(PasswordHistory.builder()
                .userId(userId)
                .passwordHash(encodedHash)
                .build());
        pruneIfNeeded(userId);
    }

    private void pruneIfNeeded(UUID userId) {
        var all = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (all.size() > historyCount) {
            passwordHistoryRepository.deleteAll(all.subList(historyCount, all.size()));
        }
    }
}
