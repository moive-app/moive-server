package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.MeetingHomeResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.*;
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
    private final String inviteBaseUrl;

    public MeetingDetailService(
            MeetingRepository meetingRepository,
            MeetingPurposeRepository meetingPurposeRepository,
            ParticipantRepository participantRepository,
            UserRepository userRepository,
            @Value("${app.invite.base-url}") String inviteBaseUrl
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingPurposeRepository = meetingPurposeRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.inviteBaseUrl = inviteBaseUrl;
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

        boolean hasSchedule = meeting.hasSchedule();
        String scheduledDate = hasSchedule && meeting.getScheduledDate() != null
                ? meeting.getScheduledDate().toString() : null;
        String scheduledTime = hasSchedule && meeting.getScheduledTime() != null
                ? meeting.getScheduledTime().toString() : null;

        return new MeetingHomeResponse(
                meeting.getId(),
                meeting.getName(),
                purpose.getPurposeType().name(),
                status.name(),
                hasSchedule,
                scheduledDate,
                scheduledTime,
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
