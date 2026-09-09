package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.MeetingPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingPurposeRepository extends JpaRepository<MeetingPurpose, Long> {
}