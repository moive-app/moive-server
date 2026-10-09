package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    Optional<Participant> findByMeetingIdAndUserIdAndLeftAtIsNull(Long meetingId, Long userId);

    List<Participant> findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(Long meetingId);

    List<Participant> findAllByUserIdAndLeftAtIsNullOrderByIdAsc(Long userId);

    List<Participant> findAllByMeetingIdInAndLeftAtIsNullOrderByJoinedAtAsc(List<Long> meetingIds);

    Optional<Participant> findByMeetingIdAndUserId(Long meetingId, Long userId);

    List<Participant> findAllByMeetingIdAndLeftAtIsNull(Long meetingId);

    long countByMeetingIdAndLeftAtIsNullAndStateNot(Long meetingId, ParticipantState state);

    // 장소 투표를 아직 하지 않은 투표 가능 참여자 수 카운트
    // - 투표 가능 참여자: 모임을 나가지 않았고, 신규 참여(NEW_RESTRICTED)가 아닌 참여자
    // - 투표 여부: 장소 투표 내역 존재 여부로 판단
    @Query("""
            select count(p)
            from Participant p
            where p.meetingId = :meetingId
              and p.leftAt is null
              and p.state <> :excludedState
              and not exists (
                  select 1
                  from PlaceVote pv
                  where pv.meetingId = p.meetingId
                    and pv.participantId = p.id
              )
            """)
    long countParticipantsYetToVote(
            @Param("meetingId") Long meetingId,
            @Param("excludedState") ParticipantState excludedState
    );
}
