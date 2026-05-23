package com.demo.app.cv.extraction;

import com.demo.app.cv.dto.CreateCvCandidateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CvExtractionResultMapperTest {

    CvExtractionResultMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CvExtractionResultMapper();
    }

    @Test
    void toRequest_mapsAllFields() {
        UUID documentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        var result = new CvExtractionResult(
                "John Doe", "john@example.com", "+1234567890", "NYC", "US",
                "https://linkedin.com/in/john", "https://github.com/john", "https://john.dev",
                "Experienced developer", List.of("Spring"), List.of("Teamwork"),
                null, null, "HIGH", List.of(), List.of("email"), "en",
                null, null, List.of("Java"), null, null
        );

        var request = mapper.toRequest(result, documentId, categoryId);

        assertThat(request.documentId()).isEqualTo(documentId);
        assertThat(request.documentCategoryId()).isEqualTo(categoryId);
        assertThat(request.fullName()).isEqualTo("John Doe");
        assertThat(request.email()).isEqualTo("john@example.com");
        assertThat(request.confidenceOverall()).isEqualTo("HIGH");
        assertThat(request.technicalSkills()).contains("Java");
    }

    @Test
    void toRequest_defaultsFullNameToUnknown_whenNull() {
        UUID documentId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        var result = new CvExtractionResult(
                null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, "LOW", null, null, null,
                null, null, null, null, null
        );

        var request = mapper.toRequest(result, documentId, categoryId);

        assertThat(request.fullName()).isEqualTo("Unknown");
    }

    @Test
    void normalizeConfidence_highMediumLow() {
        // Access via toRequest with different confidence values
        var baseResult = buildMinimalResult("HIGH");
        assertThat(mapper.toRequest(baseResult, UUID.randomUUID(), UUID.randomUUID())
                .confidenceOverall()).isEqualTo("HIGH");

        assertThat(mapper.toRequest(buildMinimalResult("MEDIUM"), UUID.randomUUID(), UUID.randomUUID())
                .confidenceOverall()).isEqualTo("MEDIUM");

        assertThat(mapper.toRequest(buildMinimalResult("low"), UUID.randomUUID(), UUID.randomUUID())
                .confidenceOverall()).isEqualTo("LOW");

        assertThat(mapper.toRequest(buildMinimalResult(null), UUID.randomUUID(), UUID.randomUUID())
                .confidenceOverall()).isEqualTo("LOW");
    }

    @Test
    void parseDate_validIsoDate() {
        // parseDate is tested via mapWorkExperiences with a valid date string
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                List.of(new CvExtractionResult.WorkExperience(
                        "Acme", "Dev", "2024-01-15", null, null, false,
                        null, null, null, null, null)),
                null, null, null, null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.workExperiences()).hasSize(1);
        assertThat(request.workExperiences().get(0).startDate()).isNotNull();
        assertThat(request.workExperiences().get(0).startDate().toString()).isEqualTo("2024-01-15");
    }

    @Test
    void parseDate_returnsNull_forInvalid() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                List.of(new CvExtractionResult.WorkExperience(
                        "Acme", "Dev", "not-a-date", null, null, false,
                        null, null, null, null, null)),
                null, null, null, null
        );

        // Should not throw
        assertThatCode(() -> mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID()))
                .doesNotThrowAnyException();

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());
        assertThat(request.workExperiences().get(0).startDate()).isNull();
    }

    @Test
    void parseDate_returnsNull_forNull() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                List.of(new CvExtractionResult.WorkExperience(
                        "Acme", "Dev", null, null, null, false,
                        null, null, null, null, null)),
                null, null, null, null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.workExperiences().get(0).startDate()).isNull();
    }

    @Test
    void mapWorkExperiences_handlesNullList() {
        var result = buildMinimalResult("HIGH");

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.workExperiences()).isNull();
    }

    @Test
    void mapWorkExperiences_defaultsNullCompanyAndTitle() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                List.of(new CvExtractionResult.WorkExperience(
                        null, null, null, null, null, false,
                        null, null, null, null, null)),
                null, null, null, null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.workExperiences()).hasSize(1);
        assertThat(request.workExperiences().get(0).company()).isEqualTo("Unknown");
        assertThat(request.workExperiences().get(0).title()).isEqualTo("Unknown");
    }

    @Test
    void mapLanguages_filtersBlankLanguage() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                null, null, null,
                List.of(
                        new CvExtractionResult.Language("English", "Native"),
                        new CvExtractionResult.Language("", "Beginner"),
                        new CvExtractionResult.Language(null, "Advanced")
                ),
                null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.languages()).hasSize(1);
        assertThat(request.languages().get(0).language()).isEqualTo("English");
    }

    @Test
    void mapEducations_handlesNullList() {
        var result = buildMinimalResult("HIGH");
        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());
        assertThat(request.educations()).isNull();
    }

    @Test
    void mapEducations_defaultsNullInstitutionAndDegree() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                null,
                List.of(new CvExtractionResult.Education(null, null, "CS", 2010, 2014, null, null)),
                null, null, null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.educations()).hasSize(1);
        assertThat(request.educations().get(0).institution()).isEqualTo("Unknown");
        assertThat(request.educations().get(0).degree()).isEqualTo("Unknown");
    }

    @Test
    void mapEducations_toShort_convertsNonNullYear() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                null,
                List.of(new CvExtractionResult.Education("MIT", "BS", "CS", 2010, 2014, null, null)),
                null, null, null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.educations().get(0).startYear()).isEqualTo((short) 2010);
        assertThat(request.educations().get(0).endYear()).isEqualTo((short) 2014);
    }

    @Test
    void mapEducations_toShort_nullYear() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                null,
                List.of(new CvExtractionResult.Education("MIT", "BS", "CS", null, null, null, null)),
                null, null, null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.educations().get(0).startYear()).isNull();
        assertThat(request.educations().get(0).endYear()).isNull();
    }

    @Test
    void mapCertifications_handlesNullList() {
        var result = buildMinimalResult("HIGH");
        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());
        assertThat(request.certifications()).isNull();
    }

    @Test
    void mapCertifications_defaultsNullName() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                null, null, null, null,
                List.of(new CvExtractionResult.Certification(null, "Amazon", null, null, "CERT-123"))
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.certifications()).hasSize(1);
        assertThat(request.certifications().get(0).name()).isEqualTo("Unknown");
    }

    @Test
    void mapCertifications_parsesValidDates() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                null, null, null, null,
                List.of(new CvExtractionResult.Certification("AWS", "Amazon", "2023-01-15", "2026-01-15", "CERT-123"))
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.certifications().get(0).issuedDate()).isNotNull();
        assertThat(request.certifications().get(0).expiryDate()).isNotNull();
    }

    @Test
    void mapLanguages_handlesNullList() {
        var result = buildMinimalResult("HIGH");
        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());
        assertThat(request.languages()).isNull();
    }

    @Test
    void parseDate_returnsNull_forBlankString() {
        var result = new CvExtractionResult(
                "Jane", null, null, null, null, null, null, null, null,
                null, null, null, null, "HIGH", null, null, null,
                List.of(new CvExtractionResult.WorkExperience(
                        "Acme", "Dev", "   ", null, null, false,
                        null, null, null, null, null)),
                null, null, null, null
        );

        var request = mapper.toRequest(result, UUID.randomUUID(), UUID.randomUUID());

        assertThat(request.workExperiences().get(0).startDate()).isNull();
    }

    private CvExtractionResult buildMinimalResult(String confidence) {
        return new CvExtractionResult(
                "Test User", null, null, null, null, null, null, null,
                null, null, null, null, null, confidence, null, null, null,
                null, null, null, null, null
        );
    }
}
