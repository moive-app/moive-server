package com.moive.MoiveBE.domain.meeting.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "preference_activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreferenceActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "preference_activity_id")
    private Long id;

    @Column(nullable = false)
    private Long preferenceId;

    @Column(nullable = false)
    private Long activityId;

    public static PreferenceActivity create(Long preferenceId, Long activityId) {
        PreferenceActivity pa = new PreferenceActivity();
        pa.preferenceId = preferenceId;
        pa.activityId = activityId;
        return pa;
    }
}
