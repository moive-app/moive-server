package com.moive.MoiveBE.domain.vote.repository;

import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {

    boolean existsByMeetingIdAndParticipantId(Long meetingId, Long participantId);

    @Query("""
            select count(distinct pv.participantId)
            from PlaceVote pv
            where pv.meetingId = :meetingId
            """)
    long countDistinctVoters(@Param("meetingId") Long meetingId);
}
