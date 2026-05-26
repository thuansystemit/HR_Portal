package com.demo.app.hiring;

import com.demo.app.hiring.controller.HiringRequestController;
import com.demo.app.hiring.dto.CreateHiringRequestRequest;
import com.demo.app.hiring.dto.HiringRequestResponse;
import com.demo.app.hiring.dto.UpdateHiringRequestStatusRequest;
import com.demo.app.hiring.service.HiringRequestService;
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
class HiringRequestControllerTest {

    @Mock HiringRequestService hiringRequestService;
    @Mock Authentication authentication;

    @InjectMocks
    HiringRequestController controller;

    private final UUID REQUEST_ID   = UUID.randomUUID();
    private final UUID REQUESTER_ID = UUID.randomUUID();

    @Test
    void create_returns201_withLocation() {
        when(authentication.getName()).thenReturn(REQUESTER_ID.toString());
        var req = new CreateHiringRequestRequest("Senior Backend Dev", "Desc", "ENGINEER", "Engineering", "HIGH");
        var response = buildResponse();
        when(hiringRequestService.create(req, REQUESTER_ID)).thenReturn(response);

        var result = controller.create(req, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().toString()).contains(REQUEST_ID.toString());
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void listAll_returns200() {
        when(hiringRequestService.listAll()).thenReturn(List.of(buildResponse()));

        var result = controller.listAll();

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void listMine_returns200() {
        when(authentication.getName()).thenReturn(REQUESTER_ID.toString());
        when(hiringRequestService.listByRequester(REQUESTER_ID)).thenReturn(List.of(buildResponse()));

        var result = controller.listMine(authentication);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getById_returns200() {
        when(hiringRequestService.getById(REQUEST_ID)).thenReturn(buildResponse());

        var result = controller.getById(REQUEST_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().id()).isEqualTo(REQUEST_ID);
    }

    @Test
    void updateStatus_returns200() {
        when(authentication.getName()).thenReturn(REQUESTER_ID.toString());
        var req = new UpdateHiringRequestStatusRequest("APPROVED", null);
        var response = buildResponse();
        when(hiringRequestService.updateStatus(REQUEST_ID, req, REQUESTER_ID)).thenReturn(response);

        var result = controller.updateStatus(REQUEST_ID, req, authentication);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(response);
    }

    @Test
    void linkPosting_returns200_withLinkedJobPosting() {
        UUID jobPostingId = UUID.randomUUID();
        when(authentication.getName()).thenReturn(REQUESTER_ID.toString());
        var response = new HiringRequestResponse(
                REQUEST_ID, REQUESTER_ID, "Jane HR",
                "Senior Backend Dev", "Desc", "ENGINEER", "Engineering",
                "HIGH", "IN_PROGRESS", jobPostingId, Instant.now(), Instant.now());
        when(hiringRequestService.linkJobPosting(REQUEST_ID, jobPostingId, REQUESTER_ID)).thenReturn(response);

        var result = controller.linkPosting(REQUEST_ID, jobPostingId, authentication);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().status()).isEqualTo("IN_PROGRESS");
        assertThat(result.getBody().jobPostingId()).isEqualTo(jobPostingId);
    }

    private HiringRequestResponse buildResponse() {
        return new HiringRequestResponse(
                REQUEST_ID, REQUESTER_ID, "Jane HR",
                "Senior Backend Dev", "Desc", "ENGINEER", "Engineering",
                "HIGH", "OPEN", null, Instant.now(), Instant.now());
    }
}
