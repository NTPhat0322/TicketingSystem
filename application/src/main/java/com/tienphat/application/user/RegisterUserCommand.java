package com.tienphat.application.user;

public record RegisterUserCommand(String email, String password, String fullName, String phone) {
}
