package com.company.tai.user.service;

import com.company.tai.common.exception.BusinessRuleException;
import com.company.tai.notification.EmailService;
import com.company.tai.security.JwtService;
import com.company.tai.user.dto.AuthResponse;
import com.company.tai.user.dto.LoginChallengeResponse;
import com.company.tai.user.dto.LoginRequest;
import com.company.tai.user.dto.RegisterRequest;
import com.company.tai.user.dto.VerifyOtpRequest;
import com.company.tai.user.entity.LoginOtp;
import com.company.tai.user.entity.Role;
import com.company.tai.user.entity.User;
import com.company.tai.user.repository.LoginOtpRepository;
import com.company.tai.user.repository.RoleRepository;
import com.company.tai.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TOKEN_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginOtpRepository loginOtpRepository;
    private final EmailService emailService;

    @Value("${app.otp.length:6}")
    private int otpLength;

    @Value("${app.otp.expiry-minutes:5}")
    private int otpExpiryMinutes;

    @Value("${app.otp.max-attempts:5}")
    private int otpMaxAttempts;

    // Never true in production: lets automated tests (and local dev, if explicitly enabled)
    // read the code back from the API response instead of an inbox, since the OTP is otherwise
    // only ever delivered by email.
    @Value("${app.otp.debug-expose-code:false}")
    private boolean debugExposeCode;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("An account with this email already exists");
        }

        // Public self-registration must never let a caller choose their own role (e.g. ADMIN) —
        // any roleName supplied on this endpoint is ignored; privileged roles can only be granted
        // afterwards by an admin via UserController/RoleController.
        Role role = roleRepository.findByName("STAFF")
                .orElseThrow(() -> new BusinessRuleException("Unknown role: STAFF"));

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .passwordHash(passwordEncoder.encode(request.password()))
                .active(true)
                .roles(Set.of(role))
                .build();

        userRepository.save(user);

        String token = jwtService.generateAccessToken(user);
        return AuthResponse.of(token, user.getId(), user.getFullName(), user.getEmail(), extractRoleNames(user));
    }

    /**
     * Step 1 of login: verifies credentials, then emails a one-time code instead of
     * issuing a JWT directly. The JWT is only issued after {@link #verifyOtp} succeeds.
     */
    @Transactional
    public LoginChallengeResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessRuleException("Invalid email or password"));

        LoginChallengeResponse challenge = issueOtp(user);
        return challenge;
    }

    @Transactional
    public LoginChallengeResponse resendOtp(String challengeToken) {
        LoginOtp previous = loginOtpRepository.findByChallengeToken(challengeToken)
                .orElseThrow(() -> new BusinessRuleException("Login session not found or expired"));
        if (previous.isConsumed()) {
            throw new BusinessRuleException("This login session has already been completed");
        }
        return issueOtp(previous.getUser());
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        LoginOtp otp = loginOtpRepository.findByChallengeToken(request.challengeToken())
                .orElseThrow(() -> new BusinessRuleException("Login session not found or expired"));

        if (otp.isConsumed()) {
            throw new BusinessRuleException("This code has already been used");
        }
        if (otp.isExpired()) {
            throw new BusinessRuleException("This code has expired. Please log in again");
        }
        if (otp.getAttempts() >= otpMaxAttempts) {
            throw new BusinessRuleException("Too many incorrect attempts. Please log in again");
        }

        if (!passwordEncoder.matches(request.code(), otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            loginOtpRepository.save(otp);
            throw new BusinessRuleException("Incorrect code");
        }

        otp.setConsumedAt(Instant.now());
        loginOtpRepository.save(otp);

        User user = otp.getUser();
        String token = jwtService.generateAccessToken(user);
        return AuthResponse.of(token, user.getId(), user.getFullName(), user.getEmail(), extractRoleNames(user));
    }

    private LoginChallengeResponse issueOtp(User user) {
        String code = generateNumericCode(otpLength);
        String challengeToken = generateChallengeToken();

        LoginOtp otp = LoginOtp.builder()
                .user(user)
                .challengeToken(challengeToken)
                .codeHash(passwordEncoder.encode(code))
                .attempts(0)
                .expiresAt(Instant.now().plus(otpExpiryMinutes, ChronoUnit.MINUTES))
                .build();
        loginOtpRepository.save(otp);

        // Always visible on the backend terminal too, so a developer running the app locally
        // doesn't have to check an inbox to log in.
        log.info("Login OTP for {}: {} (expires in {} min)", maskEmail(user.getEmail()), code, otpExpiryMinutes);

        try {
            emailService.send(
                    user.getEmail(),
                    "Your Tai verification code",
                    "Your one-time verification code is: " + code + "\n\n"
                            + "It expires in " + otpExpiryMinutes + " minutes. If you did not request this, you can ignore this email."
            );
        } catch (Exception ex) {
            // Don't let a mail-provider outage lock users out of a login attempt that already
            // passed password verification; the code is still valid and can be retrieved via
            // /resend-otp once mail delivery recovers, or via debugCode in non-production setups.
            log.error("Failed to send login OTP email to {}: {}", maskEmail(user.getEmail()), ex.getMessage());
        }

        return new LoginChallengeResponse(
                challengeToken,
                maskEmail(user.getEmail()),
                otpExpiryMinutes * 60,
                debugExposeCode ? code : null
        );
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private String generateChallengeToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(TOKEN_CHARS.charAt(RANDOM.nextInt(TOKEN_CHARS.length())));
        }
        return sb.toString();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }

    private Set<String> extractRoleNames(User user) {
        return user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
    }
}
