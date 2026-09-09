package com.moive.MoiveBE.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "date_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DateVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "date_vote_id")
    private Long id;

    @Column(nullable = false)
    private Long meetingId;

    @Column(nullable = false)
    private Long participantId;

    private LocalDate candidateDate;

    private LocalTime candidateTime;

    private LocalDateTime createdAt;

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

    public static DateVote create(Long meetingId, Long participantId, LocalDate date, LocalTime time) {
        DateVote dv = new DateVote();
        dv.meetingId = meetingId;
        dv.participantId = participantId;
        dv.candidateDate = date;
        dv.candidateTime = time;
        return dv;
    }
}
