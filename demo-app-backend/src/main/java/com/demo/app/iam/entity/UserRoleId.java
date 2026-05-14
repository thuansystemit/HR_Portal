package com.demo.app.iam.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleId implements Serializable {
    private UUID userId;
    private UUID roleId;
}
