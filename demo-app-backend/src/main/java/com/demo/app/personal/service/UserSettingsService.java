package com.demo.app.personal.service;

import com.demo.app.personal.dto.AppSettingsDto;
import com.demo.app.personal.entity.UserSettings;
import com.demo.app.personal.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository repository;

    @Transactional(readOnly = true)
    public AppSettingsDto findByUserId(UUID userId) {
        return toDto(repository.findById(userId).orElseGet(() -> defaultSettings(userId)));
    }

    @Transactional
    public AppSettingsDto update(UUID userId, AppSettingsDto dto) {
        var settings = repository.findById(userId).orElseGet(() -> defaultSettings(userId));
        settings.setTheme(dto.theme());
        settings.setLanguage(dto.language());
        settings.setDateFormat(dto.dateFormat());
        settings.setDefaultPageSize(dto.defaultPageSize());
        settings.setNotifEmail(dto.notifEmail());
        settings.setNotifPush(dto.notifPush());
        settings.setNotifDesktop(dto.notifDesktop());
        return toDto(repository.save(settings));
    }

    private UserSettings defaultSettings(UUID userId) {
        return UserSettings.builder().userId(userId).build();
    }

    private AppSettingsDto toDto(UserSettings s) {
        return new AppSettingsDto(s.getTheme(), s.getLanguage(), s.getDateFormat(),
                s.getDefaultPageSize(), s.isNotifEmail(), s.isNotifPush(), s.isNotifDesktop());
    }
}
