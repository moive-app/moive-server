package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MeetingLeaveService {

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;

    public void leaveMeeting(Long meetingId) {
        Long currentUserId = getCurrentUserId();

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.MEETING_NOT_FOUND));

        if (meeting.getStatus() == MeetingStatus.COMPLETED) {
            throw new CustomException(CustomErrorCode.CANNOT_LEAVE_COMPLETED_MEETING);
        }

        Participant myParticipant = participantRepository
                .findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, currentUserId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_A_PARTICIPANT));

        // 잔여 활성 참여자 목록 (나 제외, 가입 순)
        List<Participant> remaining = participantRepository
                .findAllByMeetingIdAndLeftAtIsNullOrderByJoinedAtAsc(meetingId)
                .stream()
                .filter(p -> !p.getUserId().equals(currentUserId))
                .toList();

        // 마지막 참여자가 나가면 모임 COMPLETED 전환
        if (remaining.isEmpty()) {
            myParticipant.leave();
            meeting.decrementParticipantCnt();
            if (myParticipant.isConditionCompleted()) meeting.decrementSubmittedCnt();
            meeting.complete();
            return;
        }

        // 모임장이 나가면 가입일시 가장 빠른 잔여 참여자로 승계
        if (meeting.getCreatorUserId().equals(currentUserId)) {
            meeting.updateCreator(remaining.get(0).getUserId());
        }

        myParticipant.leave();
        meeting.decrementParticipantCnt();
        if (myParticipant.isConditionCompleted()) meeting.decrementSubmittedCnt();
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
