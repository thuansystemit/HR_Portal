package com.demo.app.iam.controller;

import com.demo.app.iam.dto.CreateRoleRequest;
import com.demo.app.iam.dto.PagedResponse;
import com.demo.app.iam.dto.RoleResponse;
import com.demo.app.iam.dto.UpdateRoleRequest;
import com.demo.app.iam.service.RoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleService roleService;

    @InjectMocks
    private RoleController roleController;

    private final UUID ROLE_ID = UUID.randomUUID();

    private RoleResponse buildRoleResponse() {
        return new RoleResponse(ROLE_ID, "Admin", "Admin role", false,
                List.of("perm.view"), 0L, Instant.now());
    }

    @Test
    void list_returnsOk() {
        var roleResponse = buildRoleResponse();
        var paged = new PagedResponse<>(List.of(roleResponse), 0, 10, 1L, 1);

        when(roleService.list(0, 10)).thenReturn(paged);

        var result = roleController.list(0, 10);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().content()).hasSize(1);
    }

    @Test
    void create_returnsCreated() {
        var request = new CreateRoleRequest("Admin", "Admin role", Set.of());
        var roleResponse = buildRoleResponse();

        when(roleService.create(request)).thenReturn(roleResponse);

        var result = roleController.create(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getFirst("Location")).contains(ROLE_ID.toString());
        assertThat(result.getBody()).isEqualTo(roleResponse);
    }

    @Test
    void findById_returnsOk() {
        var roleResponse = buildRoleResponse();

        when(roleService.findById(ROLE_ID)).thenReturn(roleResponse);

        var result = roleController.findById(ROLE_ID);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(roleResponse);
    }

    @Test
    void update_returnsOk() {
        var request = new UpdateRoleRequest("Admin", "Updated desc", Set.of());
        var roleResponse = buildRoleResponse();

        when(roleService.update(ROLE_ID, request)).thenReturn(roleResponse);

        var result = roleController.update(ROLE_ID, request);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isEqualTo(roleResponse);
    }

    @Test
    void delete_returns204() {
        var result = roleController.delete(ROLE_ID);

        verify(roleService).delete(ROLE_ID);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
