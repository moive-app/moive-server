package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByMeetingIdAndUserIdAndLeftAtIsNull(Long meetingId, Long userId);

    List<Participant> findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(Long meetingId);

    List<Participant> findAllByUserIdAndLeftAtIsNullOrderByIdAsc(Long userId);

    List<Participant> findAllByMeetingIdInAndLeftAtIsNullOrderByJoinedAtAsc(List<Long> meetingIds);

    Optional<Participant> findByMeetingIdAndUserId(Long meetingId, Long userId);

    List<Participant> findAllByMeetingIdAndLeftAtIsNull(Long meetingId);
}
