package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.MeetingPurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MeetingPurposeRepository extends JpaRepository<MeetingPurpose, Long> {

    Optional<MeetingPurpose> findByMeetingId(Long meetingId);

    List<MeetingPurpose> findAllByMeetingIdIn(List<Long> meetingIds);
}