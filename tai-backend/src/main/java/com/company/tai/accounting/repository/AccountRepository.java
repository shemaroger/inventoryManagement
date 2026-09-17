package com.company.tai.accounting.repository;

import com.company.tai.accounting.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findAllByOrderByCodeAsc();
    Optional<Account> findByCode(String code);
}
