package com.demo.app.personal.repository;

import com.demo.app.personal.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserSettingsRepository extends JpaRepository<UserSettings, UUID> {}
