package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceLocationResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationStatus;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.recommendation.service.AreaDistanceService;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import com.moive.MoiveBE.domain.vote.dto.DateVoteResultResponse;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteRequest;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteResultResponse;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.moive.MoiveBE.global.exception.CustomErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class VoteService {

    private static final int TOP_N = 3;

    // 순위 정렬 시 조회한 유저를 특정할 필요없는 상황에서 사용
    private static final Long NO_VIEWER_PARTICIPANT_ID = -1L;

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;
    private final DateVoteRepository dateVoteRepository;
    private final PlaceVoteRepository placeVoteRepository;
    private final RecommendationRunRepository recommendationRunRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final GooglePlacesClient googlePlacesClient;
    private final AreaDistanceService areaDistanceService;

    @Value("${place-vote.deadline-days}")
    private int placeVoteDeadlineDays;

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

        // 마지막 투표자인 경우 => 득표 집계 결과 1위 장소를 모임 장소로 확정
        long voterCnt = placeVoteRepository.countDistinctVoters(meetingId);
        if(voterCnt == meeting.getParticipantCnt()) {
            confirmMeetingPlace(meeting);
        }
    }

    /**
     * 장소 투표 마감 기한이 지났는데도 전원이 투표를 마치지 못한 모임 처리
     * - Case A) 1명 이상 투표했다면 그때까지의 집계 결과로 장소 확정 o + 모임 상태 CONFIRMED로 전환
     * - Case B) 아무도 투표하지 않았다면 장소 확정 x + 모임 상태 CONFIRMED로 전환
     * - 매일 MeetingLifecycleScheduler에서 호출됨
     */
    @Transactional
    public void finalizeExpiredPlaceVotes() {
        List<Meeting> votingMeetings = meetingRepository.findAllByStatus(MeetingStatus.VOTING);
        if(votingMeetings.isEmpty()) {
            return;
        }

        // 모임별 장소 추천이 완료된 시각을 장소 투표 시작 시점으로 지정
        List<Long> meetingIds = votingMeetings.stream().map(Meeting::getId).toList();
        Map<Long, LocalDateTime> placeVoteStartedAtByMeetingId = recommendationRunRepository
                .findAllByMeetingIdInAndStatus(meetingIds, RecommendationStatus.COMPLETED)
                .stream()
                .collect(Collectors.toMap(
                        RecommendationRun::getMeetingId,
                        RecommendationRun::getUpdatedAt,
                        (earlier, later) -> earlier.isAfter(later) ? earlier : later
                ));

        LocalDateTime deadline = LocalDateTime.now().minusDays(placeVoteDeadlineDays);

        for (Meeting meeting : votingMeetings) {
            LocalDateTime placeVoteStartedAt = placeVoteStartedAtByMeetingId.get(meeting.getId());

            // 장소 투표 단계가 아니거나 마감 기한이 지나지 않았으면 스킵
            if(placeVoteStartedAt == null || placeVoteStartedAt.isAfter(deadline)) {
                continue;
            }

            confirmMeetingPlace(meeting);
        }
    }

    private void confirmMeetingPlace(Meeting meeting) {
        List<CandidateDetail> ranked = rankCandidates(meeting.getId(), NO_VIEWER_PARTICIPANT_ID);
        Long confirmedPlaceId = ranked.isEmpty() ? null : ranked.get(0).recommendedPlaceId();
        meeting.confirmPlace(confirmedPlaceId);
        log.info("[장소 투표] 장소 투표 마감 (meetingId={}, confirmedPlaceId={})", meeting.getId(), confirmedPlaceId);
    }

    /**
     * 장소 투표 현황 조회
     * - 정렬 기준: (1) 득표수 desc -> (2) 참여자 출발지 <-> 장소 간 직선거리 평균값 asc
     * - 구글 장소 조회 실패 시 (2)는 취향 일치 수 desc로 대체 -> (3) recommendedPlaceId asc
     */
    private static final Comparator<CandidateDetail> CANDIDATE_COMPARATOR = Comparator
            .comparingInt(CandidateDetail::voterCnt).reversed()
            .thenComparing(VoteService::compareByDistanceOrPreferenceMatch)
            .thenComparing(CandidateDetail::recommendedPlaceId);

    public PlaceVoteResultResponse getMeetingPlaceVoteResult(Long userId, Long meetingId) {
        // 모임 조회
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(MEETING_NOT_FOUND));

        // 조회 권한 확인: 탈퇴하지 않은 모임 참여자인지 확인
        Participant me = participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId)
                .orElseThrow(() -> new CustomException(VOTE_ACCESS_DENIED));

        // 투표 내역 집계
        boolean isVoteFinished = meeting.getStatus() == MeetingStatus.CONFIRMED || meeting.getStatus() == MeetingStatus.COMPLETED;
        int totalVoterCnt = (int) placeVoteRepository.countDistinctVoters(meetingId);

        List<CandidateDetail> ranked = rankCandidates(meetingId, me.getId());

        List<PlaceVoteResultResponse.Candidate> candidates = ranked.stream()
                .limit(TOP_N)
                .map(CandidateDetail::toResponse)
                .toList();

        return PlaceVoteResultResponse.of(isVoteFinished, totalVoterCnt, candidates);
    }

    // 장소별 투표 내역 집계
    private List<CandidateDetail> rankCandidates(Long meetingId, Long participantId) {
        List<PlaceVoteSummary> placeVotes = placeVoteRepository.aggregateByPlace(meetingId, participantId);
        if(placeVotes.isEmpty()) {
            return List.of();
        }

        // 추천 장소 정보 IN 배치 조회 -> Map<recommendedPlaceId, RecommendedPlace>
        List<Long> placeIds = placeVotes.stream().map(PlaceVoteSummary::recommendedPlaceId).toList();
        Map<Long, RecommendedPlace> placeById = recommendedPlaceRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(RecommendedPlace::getId, Function.identity()));

        // 참여자 출발지 좌표 IN 배치 조회
        List<double[]> departureCoordinates = getParticipantDepartureLocations(meetingId);

        return placeVotes.stream()
                .map(summary -> toCandidateDetail(summary, placeById.get(summary.recommendedPlaceId()), departureCoordinates))
                .sorted(CANDIDATE_COMPARATOR)
                .toList();
    }

    private List<double[]> getParticipantDepartureLocations(Long meetingId) {
        List<Participant> participants = participantRepository.findAllByMeetingIdAndLeftAtIsNull(meetingId);
        List<Long> participantIds = participants.stream().map(Participant::getId).toList();
        return participantPreferenceRepository.findAllByParticipantIdIn(participantIds).stream()
                .map(p -> new double[]{p.getDepartureLatitude().doubleValue(), p.getDepartureLongitude().doubleValue()})
                .toList();
    }

    private CandidateDetail toCandidateDetail(
            PlaceVoteSummary summary,
            RecommendedPlace recommendedPlace,
            List<double[]> departureCoordinates
    ) {
        String placeName = null;
        Double avgDistance = null;

        try {
            GooglePlaceLocationResponse googlePlace = googlePlacesClient.getPlaceLocation(recommendedPlace.getGooglePlaceId());
            if (googlePlace != null && googlePlace.location() != null) {
                placeName = googlePlace.displayName() != null ? googlePlace.displayName().text() : null;
                avgDistance = averageDistanceKm(
                        departureCoordinates, googlePlace.location().latitude(), googlePlace.location().longitude()
                );
            }
        } catch (CustomException e) {
            log.warn("[장소 투표 현황 조회] 구글 장소 조회 실패 => 정렬 기준 취향 일치 수로 대체 (recommendedPlaceId={}, errorCode={})",
                    summary.recommendedPlaceId(), e.getCustomErrorCode());
        }

        return new CandidateDetail(
                summary.recommendedPlaceId(),
                placeName,
                summary.voterCnt().intValue(),
                summary.isVotedByMe(),
                avgDistance,
                recommendedPlace.getPreferenceMatchCnt()
        );
    }

    // 참여자들의 출발지 <-> 추천 장소 간 직선거리 합의 평균값
    private Double averageDistanceKm(List<double[]> departureCoordinates, double placeLat, double placeLng) {
        return departureCoordinates.stream()
                .mapToDouble(c -> areaDistanceService.calculateDistanceKm(c[0], c[1], placeLat, placeLng))
                .average()
                .orElse(0.0);
    }

    private static int compareByDistanceOrPreferenceMatch(CandidateDetail a, CandidateDetail b) {
        if (a.avgDistanceKm() != null && b.avgDistanceKm() != null) {
            return Double.compare(a.avgDistanceKm(), b.avgDistanceKm());
        }
        return Integer.compare(b.preferenceMatchCnt(), a.preferenceMatchCnt());
    }

    private record CandidateDetail(
            Long recommendedPlaceId,
            String placeName,
            int voterCnt,
            boolean isVotedByMe,
            Double avgDistanceKm,
            int preferenceMatchCnt
    ) {
        PlaceVoteResultResponse.Candidate toResponse() {
            return PlaceVoteResultResponse.Candidate.of(recommendedPlaceId, placeName, voterCnt, isVotedByMe);
        }
    }
}
