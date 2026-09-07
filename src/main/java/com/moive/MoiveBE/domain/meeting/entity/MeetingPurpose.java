package com.moive.MoiveBE.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "meeting_purposes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingPurpose {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long meetingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurposeType purposeType;

    public static MeetingPurpose create(Long meetingId, PurposeType purposeType) {
        MeetingPurpose mp = new MeetingPurpose();
        mp.meetingId = meetingId;
        mp.purposeType = purposeType;
        return mp;
    }
}
