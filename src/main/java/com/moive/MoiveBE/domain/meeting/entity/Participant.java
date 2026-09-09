package com.moive.MoiveBE.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long meetingId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ParticipantState state;

    @Column(nullable = false)
    private boolean conditionCompleted;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }

    public static Participant create(Long meetingId, Long userId, ParticipantState state) {
        Participant p = new Participant();
        p.meetingId = meetingId;
        p.userId = userId;
        p.state = state;
        p.conditionCompleted = false;
        return p;
    }

    public void leave() {
        this.leftAt = java.time.LocalDateTime.now();
    }
}
