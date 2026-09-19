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
    // - 서로 다른 recommended_place_id라도 google_place_id(실제 장소)가 같으면 하나로 병합해서 집계
    // - recommendedPlaceId : 병합된 그룹을 대표하는 id (같은 google_place_id 중 가장 작은 recommended_place_id)
    // - voterCnt  : 해당 장소(googlePlaceId 기준)에 투표한 서로 다른 참여자 수
    // - votedByMe : 조회 유저가 해당 장소에 투표했으면 1
    @Query("""
            select new com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary(
                min(rp.id),
                count(distinct pv.participantId),
                cast(max(case when pv.participantId = :participantId then 1 else 0 end) as long)
            )
            from PlaceVote pv
            join RecommendedPlace rp on pv.recommendedPlaceId = rp.id
            where pv.meetingId = :meetingId
            group by rp.googlePlaceId
            """)
    List<PlaceVoteSummary> aggregateByPlace(
            @Param("meetingId") Long meetingId,
            @Param("participantId") Long participantId
    );

    void deleteAllByParticipantId(Long participantId);
}
