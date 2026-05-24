package com.demo.app.hiring;

import com.demo.app.hiring.controller.CvShareController;
import com.demo.app.hiring.dto.CvShareResponse;
import com.demo.app.hiring.dto.ShareCvRequest;
import com.demo.app.hiring.dto.SubmitImpressionRequest;
import com.demo.app.hiring.service.CvShareService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvShareControllerTest {

    @Mock CvShareService cvShareService;
    @Mock Authentication authentication;

    @InjectMocks
    CvShareController controller;

    private final UUID SHARE_ID     = UUID.randomUUID();
    private final UUID REQUEST_ID   = UUID.randomUUID();
    private final UUID CANDIDATE_ID = UUID.randomUUID();
    private final UUID SHARED_BY    = UUID.randomUUID();
    private final UUID SHARED_WITH  = UUID.randomUUID();

    @Test
    void share_returns201_withLocation() {
        when(authentication.getName()).thenReturn(SHARED_BY.toString());
        var req = new ShareCvRequest(CANDIDATE_ID, SHARED_WITH, "Looks promising");
        var response = buildResponse();
        when(cvShareService.share(REQUEST_ID, req, SHARED_BY)).thenReturn(response);

        var result = controller.share(REQUEST_ID, req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().toString()).contains(SHARE_ID.toString());
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void listByRequest_returns200() {
        when(cvShareService.listByRequest(REQUEST_ID)).thenReturn(List.of(buildResponse()));

        var result = controller.listByRequest(REQUEST_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getById_returns200() {
        when(cvShareService.getById(SHARE_ID)).thenReturn(buildResponse());

        var result = controller.getById(SHARE_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().id()).isEqualTo(SHARE_ID);
    }

    @Test
    void inbox_returns200() {
        when(authentication.getName()).thenReturn(SHARED_WITH.toString());
        when(cvShareService.listMySharedCvs(SHARED_WITH)).thenReturn(List.of(buildResponse()));

        var result = controller.inbox(authentication);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void submitImpression_returns200() {
        when(authentication.getName()).thenReturn(SHARED_WITH.toString());
        var req = new SubmitImpressionRequest("INTERESTED", "Great candidate");
        var response = buildResponse();
        when(cvShareService.submitImpression(SHARE_ID, req, SHARED_WITH)).thenReturn(response);

        var result = controller.submitImpression(REQUEST_ID, SHARE_ID, req, authentication);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    private CvShareResponse buildResponse() {
        return new CvShareResponse(
                SHARE_ID, REQUEST_ID, "Senior Backend Dev",
                CANDIDATE_ID, "Jane Doe", "AVAILABLE",
                SHARED_BY, "HR Manager",
                SHARED_WITH, "Dev Lead",
                null, null, Instant.now(), null);
    }
}
