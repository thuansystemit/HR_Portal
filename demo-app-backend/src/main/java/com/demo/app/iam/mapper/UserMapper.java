package com.demo.app.iam.mapper;

import com.demo.app.iam.dto.CreateUserRequest;
import com.demo.app.iam.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", expression = "java(request.status() != null ? request.status() : \"active\")")
    User toEntity(CreateUserRequest request);
}
