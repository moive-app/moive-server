package com.moive.MoiveBE.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "participant_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipantPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_id")
    private Long id;

    @Column(nullable = false)
    private Long participantId;

    @Column(nullable = false, length = 100)
    private String departureName;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal departureLatitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal departureLongitude;

    private Integer maxTravelMinutes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.submittedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static ParticipantPreference create(
            Long participantId,
            String departureName,
            BigDecimal departureLatitude,
            BigDecimal departureLongitude,
            Integer maxTravelMinutes
    ) {
        ParticipantPreference pref = new ParticipantPreference();
        pref.participantId = participantId;
        pref.departureName = departureName;
        pref.departureLatitude = departureLatitude;
        pref.departureLongitude = departureLongitude;
        pref.maxTravelMinutes = maxTravelMinutes;
        return pref;
    }

    public void update(
            String departureName,
            BigDecimal departureLatitude,
            BigDecimal departureLongitude,
            Integer maxTravelMinutes
    ) {
        this.departureName = departureName;
        this.departureLatitude = departureLatitude;
        this.departureLongitude = departureLongitude;
        this.maxTravelMinutes = maxTravelMinutes;
    }
}
