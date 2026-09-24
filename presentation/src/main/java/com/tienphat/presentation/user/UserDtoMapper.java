package com.tienphat.presentation.user;

import com.tienphat.application.user.ChangeUserRoleCommand;
import com.tienphat.application.user.UserResult;
import com.tienphat.presentation.user.dto.ChangeRoleRequest;
import com.tienphat.presentation.user.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface UserDtoMapper {

    UserResponse toResponse(UserResult result);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "newRole", source = "request.role")
    ChangeUserRoleCommand toCommand(UUID id, ChangeRoleRequest request);
}
