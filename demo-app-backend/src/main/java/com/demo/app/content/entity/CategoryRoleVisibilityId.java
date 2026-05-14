package com.demo.app.content.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRoleVisibilityId implements Serializable {
    private UUID categoryId;
    private UUID roleId;
}
