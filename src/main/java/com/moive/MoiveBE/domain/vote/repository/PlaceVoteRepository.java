package com.moive.MoiveBE.domain.vote.repository;

import com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceVoteRepository extends JpaRepository<PlaceVote, Long> {

    boolean existsByMeetingIdAndParticipantId(Long meetingId, Long participantId);

    // 장소 투표 참여 인원 수
    // - 모임을 나간 참여자의 투표 제외
    @Query("""
            select count(distinct pv.participantId)
            from PlaceVote pv
            join Participant p on pv.participantId = p.id
            where pv.meetingId = :meetingId
              and p.leftAt is null
            """)
    long countDistinctVoters(@Param("meetingId") Long meetingId);

    // 장소별 득표 집계
    // - 모임을 나간 참여자의 투표는 제외
    // - 서로 다른 recommended_place_id라도 google_place_id(실제 장소)가 같으면 하나로 병합해서 집계
    // - recommendedPlaceId: 병합된 그룹을 대표하는 ID (같은 google_place_id 중 가장 작은 recommended_place_id)
    // - voterCnt: 해당 장소(googlePlaceId 기준)에 투표한 서로 다른 참여자 수
    // - votedByMe: 조회 유저가 해당 장소에 투표했으면 1
    @Query("""
            select new com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary(
                min(rp.id),
                count(distinct pv.participantId),
                cast(max(case when pv.participantId = :participantId then 1 else 0 end) as long)
            )
            from PlaceVote pv
            join RecommendedPlace rp on pv.recommendedPlaceId = rp.id
            join Participant p on pv.participantId = p.id
            where pv.meetingId = :meetingId
              and p.leftAt is null
            group by rp.googlePlaceId
            """)
    List<PlaceVoteSummary> aggregateByPlace(
            @Param("meetingId") Long meetingId,
            @Param("participantId") Long participantId
    );

    void deleteAllByParticipantId(Long participantId);
}
