package com.demo.app.personal.service;

import com.demo.app.personal.dto.AppSettingsDto;
import com.demo.app.personal.entity.UserSettings;
import com.demo.app.personal.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock UserSettingsRepository repository;

    @InjectMocks
    UserSettingsService userSettingsService;

    private final UUID USER_ID = UUID.randomUUID();

    @Test
    void findByUserId_returnsSettings_whenExists() {
        var settings = UserSettings.builder()
                .userId(USER_ID)
                .theme("dark")
                .language("fr")
                .dateFormat("dd/MM/yyyy")
                .defaultPageSize(20)
                .notifEmail(false)
                .notifPush(true)
                .notifDesktop(false)
                .build();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(settings));

        var result = userSettingsService.findByUserId(USER_ID);

        assertThat(result.theme()).isEqualTo("dark");
        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.defaultPageSize()).isEqualTo(20);
    }

    @Test
    void findByUserId_returnsDefaults_whenNotExists() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        var result = userSettingsService.findByUserId(USER_ID);

        // Default settings from UserSettings entity defaults
        assertThat(result).isNotNull();
        assertThat(result.theme()).isNull(); // builder default is null without explicit set
    }

    @Test
    void update_savesAndReturnsUpdated() {
        var existing = UserSettings.builder().userId(USER_ID).build();
        var dto = new AppSettingsDto("light", "en", "MM/dd/yyyy", 15, true, false, true);
        var saved = UserSettings.builder()
                .userId(USER_ID)
                .theme("light")
                .language("en")
                .dateFormat("MM/dd/yyyy")
                .defaultPageSize(15)
                .notifEmail(true)
                .notifPush(false)
                .notifDesktop(true)
                .build();

        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenReturn(saved);

        var result = userSettingsService.update(USER_ID, dto);

        assertThat(result.theme()).isEqualTo("light");
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.defaultPageSize()).isEqualTo(15);
        assertThat(result.notifDesktop()).isTrue();
        verify(repository).save(any());
    }

    @Test
    void update_createsNew_whenNotExists() {
        var dto = new AppSettingsDto("system", "de", "dd.MM.yyyy", 10, true, true, false);
        var saved = UserSettings.builder()
                .userId(USER_ID)
                .theme("system")
                .language("de")
                .dateFormat("dd.MM.yyyy")
                .defaultPageSize(10)
                .notifEmail(true)
                .notifPush(true)
                .notifDesktop(false)
                .build();

        when(repository.findById(USER_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(saved);

        var result = userSettingsService.update(USER_ID, dto);

        assertThat(result.theme()).isEqualTo("system");
        verify(repository).save(any());
    }
}
