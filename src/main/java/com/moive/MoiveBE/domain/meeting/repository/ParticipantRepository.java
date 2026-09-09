package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByMeetingIdAndUserIdAndLeftAtIsNull(Long meetingId, Long userId);

    Optional<Participant> findByMeetingIdAndUserId(Long meetingId, Long userId);
}