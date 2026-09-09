package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.MeetingDetailResponse;
import com.moive.MoiveBE.domain.meeting.dto.MeetingHomeResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.*;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MeetingDetailService {

    private final MeetingRepository meetingRepository;
    private final MeetingPurposeRepository meetingPurposeRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final GooglePlacesClient googlePlacesClient;
    private final String inviteBaseUrl;

    public MeetingDetailService(
            MeetingRepository meetingRepository,
            MeetingPurposeRepository meetingPurposeRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            RecommendedPlaceRepository recommendedPlaceRepository,
            GooglePlacesClient googlePlacesClient,
            @Value("${app.invite.base-url}") String inviteBaseUrl
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingPurposeRepository = meetingPurposeRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.recommendedPlaceRepository = recommendedPlaceRepository;
        this.googlePlacesClient = googlePlacesClient;
        this.inviteBaseUrl = inviteBaseUrl;
    }

    public MeetingDetailResponse getMeetingDetail(Long meetingId) {
        Long currentUserId = getCurrentUserId();

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.MEETING_NOT_FOUND));

        Participant myParticipant = participantRepository
                .findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, currentUserId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_A_PARTICIPANT));

        MeetingPurpose purpose = meetingPurposeRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.MEETING_NOT_FOUND));

        List<Participant> participants = participantRepository
                .findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(meetingId);

        Map<Long, User> userMap = userRepository.findAllById(
                participants.stream().map(Participant::getUserId).toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        MeetingStatus status = meeting.getStatus();
        boolean isCompleted = status == MeetingStatus.COMPLETED;
        boolean isConfirmedOrCompleted = status == MeetingStatus.CONFIRMED || isCompleted;

        boolean recommendationReady = buildRecommendationReady(status, participants);

        MeetingDetailResponse.ConfirmedPlaceDto confirmedPlace = null;
        if (isConfirmedOrCompleted && meeting.getConfirmedPlaceId() != null) {
            confirmedPlace = buildConfirmedPlace(meeting.getConfirmedPlaceId());
        }

        // travelSummary, travelMinutes: place_paths 엔티티 미구현으로 null 처리
        List<MeetingDetailResponse.ParticipantDto> participantDtos = participants.stream()
                .map(p -> {
                    User user = userMap.get(p.getUserId());
                    return new MeetingDetailResponse.ParticipantDto(
                            p.getId(),
                            p.getUserId(),
                            user != null ? user.getNickname() : "",
                            user != null ? user.getProfileImageUrl() : null,
                            p.getState().name(),
                            p.getState().getLabel(),
                            false,
                            null
                    );
                })
                .toList();

        String inviteCode = isCompleted ? null : meeting.getInviteCode();
        String inviteUrl = inviteCode != null ? inviteBaseUrl + "/" + inviteCode : null;

        String confirmedDate = null;
        String confirmedTime = null;
        if (isConfirmedOrCompleted) {
            confirmedDate = meeting.getScheduledDate() != null ? meeting.getScheduledDate().toString() : null;
            confirmedTime = meeting.getScheduledTime() != null ? meeting.getScheduledTime().toString() : null;
        }

        return new MeetingDetailResponse(
                meeting.getId(),
                meeting.getName(),
                purpose.getPurposeType().name(),
                status.name(),
                status.getLabel(),
                inviteCode,
                inviteUrl,
                !isCompleted,
                recommendationReady,
                myParticipant.getState().name(),
                myParticipant.getState().getLabel(),
                participantDtos,
                confirmedPlace,
                confirmedDate,
                confirmedTime,
                null
        );
    }

    private boolean buildRecommendationReady(MeetingStatus status, List<Participant> participants) {
        if (status != MeetingStatus.CONDITION_INPUT) return false;
        long eligible = participants.stream()
                .filter(p -> p.getState() != ParticipantState.NEW_RESTRICTED)
                .count();
        long submitted = participants.stream()
                .filter(p -> p.getState() == ParticipantState.COND_DONE)
                .count();
        return eligible > 0 && submitted == eligible;
    }

    private MeetingDetailResponse.ConfirmedPlaceDto buildConfirmedPlace(Long confirmedPlaceId) {
        RecommendedPlace place = recommendedPlaceRepository.findById(confirmedPlaceId)
                .orElse(null);
        if (place == null) return null;

        GooglePlaceDetailsResponse details = googlePlacesClient.getPlaceDetails(place.getGooglePlaceId());
        if (details == null) return null;

        return new MeetingDetailResponse.ConfirmedPlaceDto(
                confirmedPlaceId,
                details.displayName() != null ? details.displayName().text() : null,
                details.primaryTypeDisplayName() != null ? details.primaryTypeDisplayName().text() : null,
                details.formattedAddress(),
                details.location() != null ? details.location().latitude() : null,
                details.location() != null ? details.location().longitude() : null
        );
    }

    public MeetingHomeResponse getMeetingHome(Long meetingId) {
        Long currentUserId = getCurrentUserId();

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.MEETING_NOT_FOUND));

        participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, currentUserId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_A_PARTICIPANT));

        MeetingPurpose purpose = meetingPurposeRepository.findByMeetingId(meetingId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.MEETING_NOT_FOUND));

        List<Participant> participants = participantRepository
                .findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(meetingId);

        Map<Long, User> userMap = userRepository.findAllById(
                participants.stream().map(Participant::getUserId).toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        MeetingStatus status = meeting.getStatus();
        boolean isCompleted = status == MeetingStatus.COMPLETED;

        String inviteCode = isCompleted ? null : meeting.getInviteCode();
        String inviteUrl = inviteCode != null ? inviteBaseUrl + "/" + inviteCode : null;

        List<MeetingHomeResponse.ParticipantDto> participantDtos = participants.stream()
                .map(p -> {
                    User user = userMap.get(p.getUserId());
                    return new MeetingHomeResponse.ParticipantDto(
                            p.getId(),
                            user != null ? user.getNickname() : "",
                            user != null ? user.getProfileImageUrl() : null,
                            p.getState().name(),
                            p.getState().getLabel()
                    );
                })
                .toList();

        HomeAction action = resolveHomeAction(status);

        return new MeetingHomeResponse(
                meeting.getId(),
                meeting.getName(),
                purpose.getPurposeType().name(),
                status.name(),
                inviteCode,
                inviteUrl,
                participantDtos,
                action.message(),
                action.actionLabel(),
                action.actionEnabled()
        );
    }

    private HomeAction resolveHomeAction(MeetingStatus status) {
        return switch (status) {
            case CONDITION_INPUT -> new HomeAction("아직 조건 입력 중이에요!", "추천 장소 확인", false);
            case VOTING -> new HomeAction("이미 조건 입력이 완료된 모임이에요!", "추천 장소 확인 및 투표", true);
            case CONFIRMED -> new HomeAction("모임이 확정됐어요, 모임 정보를 확인해보세요!", "확정된 모임 보러 가기", true);
            case COMPLETED -> new HomeAction(null, null, false);
        };
    }

    private record HomeAction(String message, String actionLabel, boolean actionEnabled) {}

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
