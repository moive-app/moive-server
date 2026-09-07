package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingRequest;
import com.moive.MoiveBE.domain.meeting.dto.CreateMeetingResponse;
import com.moive.MoiveBE.domain.meeting.dto.JoinMeetingResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.MeetingPurposeRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

@Service
@Transactional
public class MeetingService {

    private static final int MAX_PARTICIPANTS = 10;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 8;

    private final MeetingRepository meetingRepository;
    private final MeetingPurposeRepository meetingPurposeRepository;
    private final ParticipantRepository participantRepository;
    private final String inviteBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public MeetingService(
            MeetingRepository meetingRepository,
            MeetingPurposeRepository meetingPurposeRepository,
            ParticipantRepository participantRepository,
            @Value("${app.invite.base-url}") String inviteBaseUrl
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingPurposeRepository = meetingPurposeRepository;
        this.participantRepository = participantRepository;
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

        return JoinMeetingResponse.of(meeting.getId(), participant.getId(), state, false);
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