package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.DateVote;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DateVoteRepository extends JpaRepository<DateVote, Long> {

    List<DateVote> findByMeetingIdAndParticipantId(Long meetingId, Long participantId);

    void deleteByMeetingIdAndParticipantId(Long meetingId, Long participantId);

    // 일정 투표 참여 인원 수
    // - participantIds: 탈퇴하지 않은 유효 참여자 id 목록 (탈퇴한 참여자의 투표는 집계에서 제외)
    @Query("""
            select count(distinct dv.participantId)
            from DateVote dv
            where dv.meetingId = :meetingId
              and dv.participantId in :participantIds
              and dv.candidateDate is not null
              and dv.candidateTime is not null
            """)
    long countDistinctVoters(@Param("meetingId") Long meetingId, @Param("participantIds") List<Long> participantIds);

    // 날짜별 일정 투표 집계
    // - candidateTime  : 해당 날짜에서 가장 늦은 투표 시간
    // - voterCnt       : 해당 날짜에 투표한 서로 다른 참여자 수
    // - votedByMe      : 유저가 해당 날짜에 투표했으면 1
    // - participantIds : 유효 참여자 id 목록
    // - 정렬            : voterCnt desc -> candidateDate asc
    @Query("""
            select new com.moive.MoiveBE.domain.vote.dto.DateVoteSummary(
                dv.candidateDate,
                max(dv.candidateTime),
                count(distinct dv.participantId),
                cast(max(case when dv.participantId = :participantId then 1 else 0 end) as long)
            )
            from DateVote dv
            where dv.meetingId = :meetingId
              and dv.participantId in :participantIds
              and dv.candidateDate is not null
              and dv.candidateTime is not null
            group by dv.candidateDate
            order by count(distinct dv.participantId) desc, dv.candidateDate asc
            """)
    List<DateVoteSummary> aggregateTopDates(
            @Param("meetingId") Long meetingId,
            @Param("participantId") Long participantId,
            @Param("participantIds") List<Long> participantIds,
            Pageable pageable
    );
}
