package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.DateVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DateVoteRepository extends JpaRepository<DateVote, Long> {
    List<DateVote> findByMeetingIdAndParticipantId(Long meetingId, Long participantId);
    void deleteByMeetingIdAndParticipantId(Long meetingId, Long participantId);
}
