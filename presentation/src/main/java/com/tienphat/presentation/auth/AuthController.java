package com.tienphat.presentation.auth;

import com.tienphat.application.auth.LoginCommand;
import com.tienphat.application.auth.LoginResult;
import com.tienphat.application.user.RegisterUserCommand;
import com.tienphat.application.user.UserResult;
import com.tienphat.application.usecase.UseCase;
import com.tienphat.presentation.auth.dto.LoginRequest;
import com.tienphat.presentation.auth.dto.LogoutRequest;
import com.tienphat.presentation.auth.dto.RefreshRequest;
import com.tienphat.presentation.auth.dto.RegisterRequest;
import com.tienphat.presentation.auth.dto.TokenResponse;
import com.tienphat.presentation.user.UserDtoMapper;
import com.tienphat.presentation.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UseCase<RegisterUserCommand, UserResult> registerUserUseCase;
    private final UseCase<LoginCommand, LoginResult> loginUseCase;
    private final UseCase<String, LoginResult> refreshUseCase;
    private final UseCase<String, Void> logoutUseCase;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthDtoMapper authMapper;
    private final UserDtoMapper userMapper;

    public AuthController(
            UseCase<RegisterUserCommand, UserResult> registerUserUseCase,
            UseCase<LoginCommand, LoginResult> loginUseCase,
            UseCase<String, LoginResult> refreshUseCase,
            UseCase<String, Void> logoutUseCase,
            AccessTokenIssuer accessTokenIssuer,
            AuthDtoMapper authMapper,
            UserDtoMapper userMapper) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.refreshUseCase = refreshUseCase;
        this.logoutUseCase = logoutUseCase;
        this.accessTokenIssuer = accessTokenIssuer;
        this.authMapper = authMapper;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        UserResult result = registerUserUseCase.execute(authMapper.toCommand(request));
        return userMapper.toResponse(result);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginUseCase.execute(authMapper.toCommand(request));
        return tokenResponse(result);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        LoginResult result = refreshUseCase.execute(request.refreshToken());
        return tokenResponse(result);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        logoutUseCase.execute(request.refreshToken());
    }

    private TokenResponse tokenResponse(LoginResult result) {
        String accessToken = accessTokenIssuer.issue(result.userId(), result.role());
        return authMapper.toTokenResponse(result, accessToken);
    }
}
