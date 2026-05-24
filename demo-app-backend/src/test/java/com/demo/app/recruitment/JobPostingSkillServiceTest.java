package com.demo.app.recruitment;

import com.demo.app.platform.exception.ResourceNotFoundException;
import com.demo.app.recruitment.dto.JobPostingSkillDto;
import com.demo.app.recruitment.entity.JobPosting;
import com.demo.app.recruitment.entity.JobPostingSkill;
import com.demo.app.recruitment.repository.JobApplicationRepository;
import com.demo.app.recruitment.repository.JobPostingRepository;
import com.demo.app.recruitment.repository.JobPostingSkillRepository;
import com.demo.app.recruitment.service.JobPostingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobPostingSkillServiceTest {

    @Mock JobPostingRepository jobPostingRepository;
    @Mock JobApplicationRepository jobApplicationRepository;
    @Mock JobPostingSkillRepository jobPostingSkillRepository;

    @InjectMocks
    JobPostingService jobPostingService;

    private final UUID POSTING_ID = UUID.randomUUID();
    private final UUID USER_ID    = UUID.randomUUID();

    // ── getSkills ─────────────────────────────────────────────────────────────

    @Test
    void getSkills_returnsSkillList_whenPostingExists() {
        var posting = buildPosting();
        var skill1 = buildSkill("Java", true);
        var skill2 = buildSkill("Spring Boot", false);

        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(jobPostingSkillRepository.findByJobPostingId(POSTING_ID))
                .thenReturn(List.of(skill1, skill2));

        List<JobPostingSkillDto> result = jobPostingService.getSkills(POSTING_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).skillName()).isEqualTo("Java");
        assertThat(result.get(0).isRequired()).isTrue();
        assertThat(result.get(1).skillName()).isEqualTo("Spring Boot");
        assertThat(result.get(1).isRequired()).isFalse();
        verify(jobPostingSkillRepository).findByJobPostingId(POSTING_ID);
    }

    @Test
    void getSkills_returnsEmpty_whenNoSkills() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(buildPosting()));
        when(jobPostingSkillRepository.findByJobPostingId(POSTING_ID)).thenReturn(List.of());

        List<JobPostingSkillDto> result = jobPostingService.getSkills(POSTING_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getSkills_throws_whenPostingNotFound() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingService.getSkills(POSTING_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(POSTING_ID.toString());

        verify(jobPostingSkillRepository, never()).findByJobPostingId(any());
    }

    // ── setSkills ─────────────────────────────────────────────────────────────

    @Test
    void setSkills_deletesOldAndSavesNew() {
        var posting = buildPosting();
        var newSkills = List.of(
                new JobPostingSkillDto("Kotlin", true),
                new JobPostingSkillDto("Docker", false)
        );
        var savedEntities = List.of(
                buildSkill("Kotlin", true),
                buildSkill("Docker", false)
        );

        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(posting));
        when(jobPostingSkillRepository.saveAll(any())).thenReturn(savedEntities);

        List<JobPostingSkillDto> result = jobPostingService.setSkills(POSTING_ID, newSkills);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).skillName()).isEqualTo("Kotlin");
        assertThat(result.get(1).skillName()).isEqualTo("Docker");
        assertThat(result.get(1).isRequired()).isFalse();
        verify(jobPostingSkillRepository).deleteByJobPostingId(POSTING_ID);
        verify(jobPostingSkillRepository).saveAll(any());
    }

    @Test
    void setSkills_savesEmptyList_whenInputEmpty() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.of(buildPosting()));
        when(jobPostingSkillRepository.saveAll(any())).thenReturn(List.of());

        List<JobPostingSkillDto> result = jobPostingService.setSkills(POSTING_ID, List.of());

        assertThat(result).isEmpty();
        verify(jobPostingSkillRepository).deleteByJobPostingId(POSTING_ID);
        verify(jobPostingSkillRepository).saveAll(argThat(list -> ((List<?>) list).isEmpty()));
    }

    @Test
    void setSkills_throws_whenPostingNotFound() {
        when(jobPostingRepository.findById(POSTING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingService.setSkills(POSTING_ID,
                List.of(new JobPostingSkillDto("Java", true))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(POSTING_ID.toString());

        verify(jobPostingSkillRepository, never()).deleteByJobPostingId(any());
        verify(jobPostingSkillRepository, never()).saveAll(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JobPosting buildPosting() {
        return JobPosting.builder()
                .id(POSTING_ID)
                .title("Senior Dev")
                .department("Engineering")
                .location("Remote")
                .status("OPEN")
                .createdBy(USER_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private JobPostingSkill buildSkill(String name, boolean required) {
        return JobPostingSkill.builder()
                .id(UUID.randomUUID())
                .jobPostingId(POSTING_ID)
                .skillName(name)
                .isRequired(required)
                .build();
    }
}
