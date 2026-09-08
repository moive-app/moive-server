package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.PreferenceActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreferenceActivityRepository extends JpaRepository<PreferenceActivity, Long> {
    List<PreferenceActivity> findByPreferenceId(Long preferenceId);
    void deleteByPreferenceId(Long preferenceId);
}
