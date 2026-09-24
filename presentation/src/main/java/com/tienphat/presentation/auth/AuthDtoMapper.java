package com.tienphat.presentation.auth;

import com.tienphat.application.auth.LoginCommand;
import com.tienphat.application.auth.LoginResult;
import com.tienphat.application.user.RegisterUserCommand;
import com.tienphat.presentation.auth.dto.LoginRequest;
import com.tienphat.presentation.auth.dto.RegisterRequest;
import com.tienphat.presentation.auth.dto.TokenResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthDtoMapper {

    RegisterUserCommand toCommand(RegisterRequest request);

    LoginCommand toCommand(LoginRequest request);

    default TokenResponse toTokenResponse(LoginResult result, String accessToken) {
        return new TokenResponse(accessToken, result.refreshToken());
    }
}
