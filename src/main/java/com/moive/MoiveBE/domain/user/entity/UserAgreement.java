package com.moive.MoiveBE.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgreementType type;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false)
    private boolean agreed;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    public static UserAgreement create(
            User user,
            AgreementType type,
            String version,
            boolean agreed
    ) {
        UserAgreement agreement = new UserAgreement();
        agreement.user = user;
        agreement.type = type;
        agreement.version = version;
        agreement.agreed = agreed;
        agreement.agreedAt = LocalDateTime.now();

        return agreement;
    }
}