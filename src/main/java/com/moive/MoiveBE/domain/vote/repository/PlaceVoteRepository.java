package com.moive.MoiveBE.domain.vote.repository;

import com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {

    boolean existsByMeetingIdAndParticipantId(Long meetingId, Long participantId);

    @Query("""
            select count(distinct pv.participantId)
            from PlaceVote pv
            where pv.meetingId = :meetingId
            """)
    long countDistinctVoters(@Param("meetingId") Long meetingId);

    // 장소별 득표 집계
    // - voterCnt  : 해당 장소에 투표한 서로 다른 참여자 수
    // - votedByMe : 조회 유저가 해당 장소에 투표했으면 1
    @Query("""
            select new com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary(
                pv.recommendedPlaceId,
                count(distinct pv.participantId),
                cast(max(case when pv.participantId = :participantId then 1 else 0 end) as long)
            )
            from PlaceVote pv
            where pv.meetingId = :meetingId
            group by pv.recommendedPlaceId
            """)
    List<PlaceVoteSummary> aggregateByPlace(
            @Param("meetingId") Long meetingId,
            @Param("participantId") Long participantId
    );

    void deleteAllByParticipantId(Long participantId);
}
