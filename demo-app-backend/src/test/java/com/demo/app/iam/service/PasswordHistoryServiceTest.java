package com.demo.app.iam.service;

import com.demo.app.iam.entity.PasswordHistory;
import com.demo.app.iam.repository.PasswordHistoryRepository;
import com.demo.app.platform.exception.PasswordPolicyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceTest {

    @Mock PasswordHistoryRepository passwordHistoryRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks
    PasswordHistoryService service;

    private final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "historyCount", 5);
    }

    // --- checkNotReused ---

    @Test
    void checkNotReused_passes_whenNoHistory() {
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());

        assertThatCode(() -> service.checkNotReused(USER_ID, "newPass")).doesNotThrowAnyException();
    }

    @Test
    void checkNotReused_passes_whenPasswordNotInHistory() {
        var h = history("$stored");
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(h));
        when(passwordEncoder.matches("newPass", "$stored")).thenReturn(false);

        assertThatCode(() -> service.checkNotReused(USER_ID, "newPass")).doesNotThrowAnyException();
    }

    @Test
    void checkNotReused_throws_whenPasswordMatchesRecentEntry() {
        var h = history("$stored");
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(h));
        when(passwordEncoder.matches("reusedPass", "$stored")).thenReturn(true);

        assertThatThrownBy(() -> service.checkNotReused(USER_ID, "reusedPass"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("recently used")));
    }

    @Test
    void checkNotReused_checksOnlyLastN_entries() {
        // history has 6 entries; only the first 5 (most recent) should be checked
        List<PasswordHistory> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            history.add(history("$hash" + i));
            when(passwordEncoder.matches("newPass", "$hash" + i)).thenReturn(false);
        }
        // 6th entry (oldest, beyond window) — matches but must NOT be checked
        history.add(history("$oldHash"));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(history);

        assertThatCode(() -> service.checkNotReused(USER_ID, "newPass")).doesNotThrowAnyException();
        verify(passwordEncoder, never()).matches("newPass", "$oldHash");
    }

    @Test
    void checkNotReused_violationMessage_includesHistoryCount() {
        var h = history("$stored");
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(h));
        when(passwordEncoder.matches("reused", "$stored")).thenReturn(true);

        assertThatThrownBy(() -> service.checkNotReused(USER_ID, "reused"))
                .isInstanceOf(PasswordPolicyException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyException) ex).getViolations())
                        .anyMatch(v -> v.contains("5")));
    }

    // --- record ---

    @Test
    void record_savesNewEntry() {
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());
        when(passwordHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.record(USER_ID, "$encoded");

        var captor = ArgumentCaptor.forClass(PasswordHistory.class);
        verify(passwordHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$encoded");
    }

    @Test
    void record_prunesOldEntries_whenHistoryExceedsLimit() {
        // After save, pretend 7 entries exist — oldest 2 should be pruned
        var all = new ArrayList<PasswordHistory>();
        for (int i = 0; i < 7; i++) all.add(history("$h" + i));

        when(passwordHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(all);

        service.record(USER_ID, "$newest");

        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(passwordHistoryRepository).deleteAll(captor.capture());
        var deleted = (List<PasswordHistory>) captor.getValue();
        assertThat(deleted).hasSize(2);
        assertThat(deleted).containsExactlyElementsOf(all.subList(5, 7));
    }

    @Test
    void record_doesNotPrune_whenHistoryWithinLimit() {
        when(passwordHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(history("$h1"), history("$h2")));

        service.record(USER_ID, "$new");

        verify(passwordHistoryRepository, never()).deleteAll(anyIterable());
    }

    @Test
    void record_doesNotPrune_whenHistoryExactlyAtLimit() {
        List<PasswordHistory> exactly5 = new ArrayList<>();
        for (int i = 0; i < 5; i++) exactly5.add(history("$h" + i));

        when(passwordHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(exactly5);

        service.record(USER_ID, "$new");

        verify(passwordHistoryRepository, never()).deleteAll(anyIterable());
    }

    private PasswordHistory history(String hash) {
        return PasswordHistory.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .passwordHash(hash)
                .createdAt(Instant.now())
                .build();
    }
}
