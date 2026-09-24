package com.tienphat.application.user;

import com.tienphat.domain.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResult toResult(User user);
}
