package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.MEETING_NOT_FOUND;
import static com.moive.MoiveBE.global.exception.CustomErrorCode.VOTE_ACCESS_DENIED;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoteService {

    private static final int TOP_N = 3;

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final DateVoteRepository dateVoteRepository;

    public DateVoteResultResponse getMeetingScheduleVoteResult(Long userId, Long meetingId) {
        // 모임 조회
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(MEETING_NOT_FOUND));

        // 조회 권한 확인: 탈퇴하지 않은 모임 참여자인지 확인
        Participant participant = participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId)
                .orElseThrow(() -> new CustomException(VOTE_ACCESS_DENIED));

        // 모임 생성 시 일정 확정한 경우 => 바로 일정 확정 (투표 생략)
        if (isScheduleConfirmed(meeting)) {
            return DateVoteResultResponse.of(
                    true,
                    null,
                    List.of(DateVoteResultResponse.Candidate.of(
                            meeting.getScheduledDate(),
                            meeting.getScheduledTime(),
                            null,
                            false
                    ))
            );
        }

        // 일정 미확정인 경우 => DateVote 내역 집계
        // - 투표 참여 인원
        int totalVoterCnt = (int) dateVoteRepository.countDistinctVoters(meetingId);

        // - 날짜별 집계
        List<DateVoteResultResponse.Candidate> candidates = dateVoteRepository
                .aggregateTopDates(meetingId, participant.getId(), PageRequest.of(0, TOP_N))
                .stream()
                .map(this::toCandidate)
                .toList();

        return DateVoteResultResponse.of(false, totalVoterCnt, candidates);
    }

    private boolean isScheduleConfirmed(Meeting meeting) {
        return meeting.getScheduledDate() != null && meeting.getScheduledTime() != null;
    }

    private DateVoteResultResponse.Candidate toCandidate(DateVoteSummary aggregate) {
        return DateVoteResultResponse.Candidate.of(
                aggregate.candidateDate(),
                aggregate.candidateTime(),
                aggregate.voterCnt().intValue(),
                aggregate.isVotedByMe()
        );
    }
}
