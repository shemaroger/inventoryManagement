package com.company.tai.user.repository;

import com.company.tai.user.entity.LoginOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginOtpRepository extends JpaRepository<LoginOtp, Long> {
    Optional<LoginOtp> findByChallengeToken(String challengeToken);
}
