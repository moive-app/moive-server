package com.moive.MoiveBE.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "meetings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long creatorUserId;

    private Long confirmedPlaceId;

    @Column(nullable = false, length = 20)
    private String name;

    private LocalDate scheduledDate;

    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MeetingStatus status;

    @Column(nullable = false)
    private int participantCnt;

    @Column(nullable = false)
    private int submittedCnt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 255)
    private String inviteCode;

    @Column(length = 255)
    private String inviteCodeExpiresAt;

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

    public static Meeting create(
            Long creatorUserId,
            String name,
            LocalDate scheduledDate,
            LocalTime scheduledTime,
            String inviteCode
    ) {
        Meeting meeting = new Meeting();
        meeting.creatorUserId = creatorUserId;
        meeting.name = name;
        meeting.scheduledDate = scheduledDate;
        meeting.scheduledTime = scheduledTime;
        meeting.status = MeetingStatus.CONDITION_INPUT;
        meeting.participantCnt = 1;
        meeting.submittedCnt = 0;
        meeting.inviteCode = inviteCode;
        return meeting;
    }

    public void incrementParticipantCnt() {
        this.participantCnt++;
    }

    public void decrementParticipantCnt() {
        this.participantCnt--;
    }

    public void incrementSubmittedCnt() {
        this.submittedCnt++;
    }

    public void decrementSubmittedCnt() {
        if (this.submittedCnt > 0) this.submittedCnt--;
    }

    public void updateCreator(Long newCreatorUserId) {
        this.creatorUserId = newCreatorUserId;
    }

    public void transitionToVoting() {
        this.status = MeetingStatus.VOTING;
    }

    public void complete() {
        this.status = MeetingStatus.COMPLETED;
    }

    public boolean hasSchedule() {
        return this.scheduledDate != null;
    }

    public void confirmPlace(Long placeId) {
        this.confirmedPlaceId = placeId;
        this.status = MeetingStatus.CONFIRMED;
    }
}