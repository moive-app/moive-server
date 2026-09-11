package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingRequest;
import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingResponse;
import com.moive.MoiveBE.domain.meeting.dto.JoinMeetingResponse;
import com.moive.MoiveBE.domain.meeting.dto.SubmitPreferenceRequest;
import com.moive.MoiveBE.domain.meeting.dto.SubmitPreferenceResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.*;
import com.moive.MoiveBE.domain.notification.entity.NotificationType;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.service.AreaRecommendationService;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class MeetingService {

    private static final int MAX_PARTICIPANTS = 10;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 8;

    private static final List<Integer> VALID_TRAVEL_MINUTES = List.of(30, 60, 90);

    private final MeetingRepository meetingRepository;
    private final MeetingPurposeRepository meetingPurposeRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository preferenceRepository;
    private final PreferenceActivityRepository preferenceActivityRepository;
    private final ActivityRepository activityRepository;
    private final DateVoteRepository dateVoteRepository;
    private final NotificationService notificationService;
    private final AreaRecommendationService areaRecommendationService;
    private final String inviteBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public MeetingService(
            MeetingRepository meetingRepository,
            MeetingPurposeRepository meetingPurposeRepository,
            ParticipantRepository participantRepository,
            ParticipantPreferenceRepository preferenceRepository,
            PreferenceActivityRepository preferenceActivityRepository,
            ActivityRepository activityRepository,
            DateVoteRepository dateVoteRepository,
            NotificationService notificationService,
            AreaRecommendationService areaRecommendationService,
            @Value("${app.invite.base-url}") String inviteBaseUrl
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingPurposeRepository = meetingPurposeRepository;
        this.participantRepository = participantRepository;
        this.preferenceRepository = preferenceRepository;
        this.preferenceActivityRepository = preferenceActivityRepository;
        this.activityRepository = activityRepository;
        this.dateVoteRepository = dateVoteRepository;
        this.notificationService = notificationService;
        this.areaRecommendationService = areaRecommendationService;
        this.inviteBaseUrl = inviteBaseUrl;
    }

    public CreateMeetingResponse createMeeting(CreateMeetingRequest request) {
        Long userId = getCurrentUserId();

        // 이름 검증
        if (request.name() == null || request.name().isBlank()) {
            throw new CustomException(CustomErrorCode.MEETING_NAME_EMPTY);
        }
        String trimmedName = request.name().trim();
        if (trimmedName.length() > 20) {
            throw new CustomException(CustomErrorCode.MEETING_NAME_TOO_LONG);
        }

        // 일정 검증
        boolean hasSchedule = Boolean.TRUE.equals(request.hasSchedule());
        if (hasSchedule && (request.scheduledDate() == null || request.scheduledTime() == null)) {
            throw new CustomException(CustomErrorCode.MEETING_SCHEDULE_INCOMPLETE);
        }

        // 모임 목적 검증
        if (request.purposeType() == null) {
            throw new CustomException(CustomErrorCode.MEETING_PURPOSE_EMPTY);
        }
        PurposeType purposeType;
        try {
            purposeType = PurposeType.valueOf(request.purposeType());
        } catch (IllegalArgumentException e) {
            throw new CustomException(CustomErrorCode.MEETING_PURPOSE_EMPTY);
        }

        // 날짜/시간 파싱
        LocalDate scheduledDate = hasSchedule ? LocalDate.parse(request.scheduledDate()) : null;
        LocalTime scheduledTime = hasSchedule ? LocalTime.parse(request.scheduledTime()) : null;

        // 초대 코드 생성
        String inviteCode = generateUniqueInviteCode();

        // 모임 저장
        Meeting meeting = meetingRepository.save(
                Meeting.create(userId, trimmedName, scheduledDate, scheduledTime, inviteCode)
        );

        // 모임 목적 저장
        meetingPurposeRepository.save(MeetingPurpose.create(meeting.getId(), purposeType));

        // 모임장 참여자 등록
        participantRepository.save(Participant.create(meeting.getId(), userId, ParticipantState.COND_PENDING));

        // NOTI-001: 조건 입력 요청 (방장)
        notificationService.sendNotification(userId, meeting.getId(), NotificationType.COND_INPUT,
                meeting.getName() + "의 조건을 아직 입력하지 않았어요.");

        String inviteUrl = inviteBaseUrl + "/" + inviteCode;
        return CreateMeetingResponse.from(meeting, purposeType, inviteUrl);
    }

    public JoinMeetingResponse joinMeeting(String inviteCode) {
        Long userId = getCurrentUserId();

        Meeting meeting = meetingRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new CustomException(CustomErrorCode.INVALID_INVITE_CODE));

        if (meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new CustomException(CustomErrorCode.MEETING_COMPLETED);
        }

        // 이미 참여 중이면 기존 상태 그대로 반환 (idempotent)
        Optional<Participant> existing =
                participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meeting.getId(), userId);
        if (existing.isPresent()) {
            Participant p = existing.get();
            return JoinMeetingResponse.of(meeting.getId(), p.getId(), p.getState(), true);
        }

        // 정원 초과 확인
        if (meeting.getParticipantCnt() >= MAX_PARTICIPANTS) {
            throw new CustomException(CustomErrorCode.MEETING_FULL);
        }

        // 참여자 상태 결정 (모임 status 기준)
        ParticipantState state = switch (meeting.getStatus()) {
            case CONDITION_INPUT -> ParticipantState.COND_PENDING;
            case VOTING, CONFIRMED -> ParticipantState.NEW_RESTRICTED;
            case COMPLETED -> throw new CustomException(CustomErrorCode.MEETING_COMPLETED);
        };

        Participant participant = participantRepository.save(
                Participant.create(meeting.getId(), userId, state)
        );
        meeting.incrementParticipantCnt();

        // NOTI-001: 조건 입력 요청 (CONDITION_INPUT 모임에 합류한 참여자만)
        if (state == ParticipantState.COND_PENDING) {
            notificationService.sendNotification(userId, meeting.getId(), NotificationType.COND_INPUT,
                    meeting.getName() + "의 조건을 아직 입력하지 않았어요.");
        }

        return JoinMeetingResponse.of(meeting.getId(), participant.getId(), state, false);
    }

    public SubmitPreferenceResponse submitPreference(Long meetingId, SubmitPreferenceRequest request) {
        Long userId = getCurrentUserId();

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.MEETING_NOT_FOUND));

        if (meeting.getStatus() != MeetingStatus.CONDITION_INPUT) {
            throw new CustomException(CustomErrorCode.MEETING_STATUS_INVALID_FOR_CONDITION);
        }

        Participant participant = participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_A_PARTICIPANT));

        // 출발지 검증
        if (request.departureName() == null || request.departureName().isBlank()
                || request.departureLatitude() == null || request.departureLongitude() == null) {
            throw new CustomException(CustomErrorCode.DEPARTURE_MISSING);
        }

        // 이동시간 검증 (null=상관없어요 허용, 그 외 30/60/90만 허용)
        if (request.maxTravelMinutes() != null && !VALID_TRAVEL_MINUTES.contains(request.maxTravelMinutes())) {
            throw new CustomException(CustomErrorCode.MAX_TRAVEL_MINUTES_INVALID);
        }

        // 취향 검증
        if (request.activityTypes() == null || request.activityTypes().isEmpty()) {
            throw new CustomException(CustomErrorCode.ACTIVITY_TYPES_EMPTY);
        }

        // 후보 일정 검증 (hasSchedule=false 모임만)
        if (!meeting.hasSchedule()) {
            if (request.availableSchedules() == null || request.availableSchedules().isEmpty()) {
                throw new CustomException(CustomErrorCode.AVAILABLE_SCHEDULES_EMPTY);
            }
        }

        // 선호조건 UPSERT
        boolean isFirstSubmit = !participant.isConditionCompleted();
        ParticipantPreference preference = preferenceRepository.findByParticipantId(participant.getId())
                .orElse(null);

        if (preference == null) {
            preference = preferenceRepository.save(ParticipantPreference.create(
                    participant.getId(),
                    request.departureName(),
                    request.departureLatitude(),
                    request.departureLongitude(),
                    request.maxTravelMinutes()
            ));
        } else {
            preference.update(
                    request.departureName(),
                    request.departureLatitude(),
                    request.departureLongitude(),
                    request.maxTravelMinutes()
            );
        }

        // 취향 UPSERT (기존 삭제 후 재저장)
        preferenceActivityRepository.deleteByPreferenceId(preference.getId());
        Long prefId = preference.getId();
        for (ActivityType activityType : request.activityTypes()) {
            Activity activity = activityRepository.findByName(activityType)
                    .orElseThrow(() -> new CustomException(CustomErrorCode.INVALID_INPUT));
            preferenceActivityRepository.save(PreferenceActivity.create(prefId, activity.getId()));
        }

        // 후보 일정 UPSERT (기존 삭제 후 재저장)
        dateVoteRepository.deleteByMeetingIdAndParticipantId(meetingId, participant.getId());
        if (!meeting.hasSchedule() && request.availableSchedules() != null) {
            for (SubmitPreferenceRequest.AvailableSchedule schedule : request.availableSchedules()) {
                dateVoteRepository.save(DateVote.create(
                        meetingId,
                        participant.getId(),
                        LocalDate.parse(schedule.date()),
                        LocalTime.parse(schedule.time())
                ));
            }
        }

        // 참여자 상태 전환
        if (isFirstSubmit) {
            participant.completeCondition();
            meeting.incrementSubmittedCnt();

            // NOTI-001 자동 읽음: 조건 입력 완료 시
            notificationService.autoReadByType(userId, meetingId, NotificationType.COND_INPUT);
        }

        // 전원 완료 시 모임 상태 전환 및 추천 트리거
        boolean triggered = false;
        if (isFirstSubmit && meeting.getSubmittedCnt() >= meeting.getParticipantCnt()) {
            meeting.transitionToVoting();
            triggered = true;
            areaRecommendationService.recommend(meetingId);

            // NOTI-003: 장소 투표 완료 요청 (전체 참여자)
            List<Participant> allParticipants = participantRepository
                    .findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(meetingId);
            for (Participant p : allParticipants) {
                notificationService.sendNotification(p.getUserId(), meetingId, NotificationType.PLACE_VOTE,
                        meeting.getName() + "의 장소 투표를 아직 완료하지 않았어요.");
            }
        }

        return SubmitPreferenceResponse.of(
                meetingId,
                participant.getId(),
                participant.getState(),
                meeting.getStatus(),
                triggered
        );
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateInviteCode();
        } while (meetingRepository.existsByInviteCode(code));
        return code;
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(secureRandom.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}