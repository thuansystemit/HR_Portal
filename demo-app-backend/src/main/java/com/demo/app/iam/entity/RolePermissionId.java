package com.demo.app.iam.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionId implements Serializable {
    private UUID roleId;
    private UUID permissionId;
}
