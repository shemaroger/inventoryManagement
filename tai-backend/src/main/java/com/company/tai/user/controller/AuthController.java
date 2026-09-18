package com.company.tai.user.controller;

import com.company.tai.common.dto.ApiResponse;
import com.company.tai.user.dto.AuthResponse;
import com.company.tai.user.dto.ChallengeTokenRequest;
import com.company.tai.user.dto.LoginChallengeResponse;
import com.company.tai.user.dto.LoginRequest;
import com.company.tai.user.dto.RegisterRequest;
import com.company.tai.user.dto.VerifyOtpRequest;
import com.company.tai.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok("User registered successfully", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginChallengeResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("Verification code sent to your email", authService.login(request));
    }

    @PostMapping("/verify-otp")
    public ApiResponse<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ApiResponse.ok("Login successful", authService.verifyOtp(request));
    }

    @PostMapping("/resend-otp")
    public ApiResponse<LoginChallengeResponse> resendOtp(@Valid @RequestBody ChallengeTokenRequest request) {
        return ApiResponse.ok("A new verification code has been sent", authService.resendOtp(request.challengeToken()));
    }
}
