package com.moive.MoiveBE.domain.vote.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Table(name = "place_votes")
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_vote_id")
    private Long id;

    @Column(nullable = false)
    private Long meetingId;

    @Column(nullable = false)
    private Long participantId;

    @Column(nullable = false)
    private Long recommendedPlaceId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    private PlaceVote(
            Long meetingId,
            Long participantId,
            Long recommendedPlaceId
    ) {
        this.meetingId = meetingId;
        this.participantId = participantId;
        this.recommendedPlaceId = recommendedPlaceId;
    }

    public static PlaceVote create(
            Long meetingId,
            Long participantId,
            Long recommendedPlaceId
    ) {
        return PlaceVote.builder()
                .meetingId(meetingId)
                .participantId(participantId)
                .recommendedPlaceId(recommendedPlaceId)
                .build();
    }
}
