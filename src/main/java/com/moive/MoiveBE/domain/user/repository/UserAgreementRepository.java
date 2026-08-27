package com.moive.MoiveBE.domain.user.repository;

import com.moive.MoiveBE.domain.user.entity.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository
        extends JpaRepository<UserAgreement, Long> {
}