package com.demo.app.personal.controller;

import com.demo.app.personal.dto.AppSettingsDto;
import com.demo.app.personal.service.UserSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSettingsControllerTest {

    @Mock
    private UserSettingsService settingsService;

    @InjectMocks
    private UserSettingsController userSettingsController;

    private final UUID USER_ID = UUID.randomUUID();

    private AppSettingsDto buildSettings() {
        return new AppSettingsDto("dark", "en", "dd/MM/yyyy", 10, true, false, false);
    }

    @Test
    void get_returnsOk() {
        var userId = USER_ID.toString();
        var settings = buildSettings();

        when(settingsService.findByUserId(USER_ID)).thenReturn(settings);

        var result = userSettingsController.get(userId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(settings);
    }

    @Test
    void update_returnsOk() {
        var userId = USER_ID.toString();
        var settings = buildSettings();

        when(settingsService.update(USER_ID, settings)).thenReturn(settings);

        var result = userSettingsController.update(userId, settings);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(settings);
    }
}
