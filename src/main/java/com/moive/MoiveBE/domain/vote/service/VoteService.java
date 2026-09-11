package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteRequest;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class VoteService {

    private static final int TOP_N = 3;

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final DateVoteRepository dateVoteRepository;
    private final PlaceVoteRepository placeVoteRepository;
    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;

    /**
     * 일정 투표 현황 조회
     */
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

    /**
     * 장소 투표 생성
     */
    @Transactional
    public void createPlaceVote(Long userId, Long meetingId, PlaceVoteRequest request) {
        // 모임 조회
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(MEETING_NOT_FOUND));

        // 모임 진행 상황 검증
        // - 투표 진행 가능 단계인지 확인
        switch (meeting.getStatus()) {
            case CONFIRMED, COMPLETED -> throw new CustomException(PLACE_VOTE_CLOSED);
            case CONDITION_INPUT -> throw new CustomException(PLACE_VOTE_NOT_STARTED);
        }

        // - 추천 장소 미생성 여부 확인
        RecommendationRun recommendationRun = recommendationRunRepository
                .findTopByMeetingIdAndStatusOrderByCreatedAtDesc(meetingId, RecommendationStatus.COMPLETED)
                .orElseThrow(() -> new CustomException(PLACE_VOTE_NOT_STARTED));

        // 유저의 투표 권한 검증
        // - 모임 내 유효한 참여자이지
        Participant participant = participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId)
                .orElseThrow(() -> new CustomException(PLACE_VOTE_ACCESS_DENIED));

        // - 기투표 여부 확인
        if(placeVoteRepository.existsByMeetingIdAndParticipantId(meetingId, participant.getId())) {
            throw new CustomException(PLACE_VOTE_ALREADY_DONE);
        }

        // 투표한 장소 ID 목록 유효성 검증
        // - 해당 모임에 추천된 장소 목록 조회
        Set<Long> validPlaceIds = new HashSet<>(
                recommendedPlaceRepository.findIdsByRecommendationRunId(recommendationRun.getId())
        );
        if(validPlaceIds.isEmpty()) {
            throw new CustomException(PLACE_VOTE_NOT_STARTED);
        }

        // - 투표한 장소 목록이 모두 추천 장소 목록에 속하는지 확인
        List<Long> selectedPlaceIds = request.recommendedPlaceIds().stream().distinct().toList();
        if(!validPlaceIds.containsAll(selectedPlaceIds)) {
            throw new CustomException(PLACE_VOTE_INVALID_PLACE);
        }

        // 투표 내역 저장
        List<PlaceVote> placeVotes = selectedPlaceIds.stream()
                .map(placeId -> PlaceVote.create(meetingId, participant.getId(), placeId))
                .toList();
        placeVoteRepository.saveAll(placeVotes);

        // 마지막 투표자 여부 확인
        long voterCnt = placeVoteRepository.countDistinctVoters(meetingId);
        if(voterCnt >= meeting.getParticipantCnt()) {
            // TODO 마지막 투표자인 경우 모임 상태 및 장소 확정 처리
            log.info("[장소 투표] 마지막 투표자입니다. meetingId={}, participantId={}", meetingId, participant.getId());
        }

    }
}
