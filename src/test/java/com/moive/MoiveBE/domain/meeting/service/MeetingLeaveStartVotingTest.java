package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.service.AreaRecommendationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * [모임 나가기 API] 모임 나가기 후 조건 입력 완료 여부 재판정 테스트
 * - 조건 입력 단계에서 참여자가 나가 남은 참여자가 모두 조건 입력을 마친 상태가 되면 => VOTING 전환 + 장소 추천 시작 여부 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:leave-start-voting;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=20",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never"
})
class MeetingLeaveStartVotingTest {

    @Autowired private MeetingLeaveService meetingLeaveService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private ParticipantRepository participantRepository;

    @MockitoBean private AreaRecommendationService areaRecommendationService;
    @MockitoBean private NotificationService notificationService;

    @AfterEach
    void tearDown() {
        participantRepository.deleteAllInBatch();
        meetingRepository.deleteAllInBatch();
    }

    @Test
    void 조건을_입력하지_않은_마지막_참여자가_나가면_장소_투표가_시작된다() {
        // given: A, B는 조건 입력 완료, C는 미입력
        Long meetingId = conditionInputMeeting(List.of(1L, 2L), List.of(3L));

        // when: C가 모임을 나감
        asUser(3L, () -> meetingLeaveService.leaveMeeting(meetingId));

        // then: 남은 A, B가 모두 조건 입력을 마친 상태 => 장소 투표 단계로 전환, 장소 추천 한 번 시작
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.VOTING);
        assertThat(meeting.getParticipantCnt()).isEqualTo(2);
        assertThat(meeting.getSubmittedCnt()).isEqualTo(2);
        verify(areaRecommendationService, times(1)).recommend(meetingId);
    }

    @Test
    void 조건을_입력한_참여자가_나가면_미입력자가_남아있으므로_장소_투표가_시작되지_않는다() {
        // given: A, B는 조건 입력 완료, C는 미입력
        Long meetingId = conditionInputMeeting(List.of(1L, 2L), List.of(3L));

        // when: 조건을 입력한 B가 모임을 나감
        asUser(2L, () -> meetingLeaveService.leaveMeeting(meetingId));

        // then: C가 아직 미입력 => 조건 입력 단계 유지, 장소 추천 시작 x
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONDITION_INPUT);
        assertThat(meeting.getParticipantCnt()).isEqualTo(2);
        assertThat(meeting.getSubmittedCnt()).isEqualTo(1);
        verify(areaRecommendationService, never()).recommend(anyLong());
    }

    private Long conditionInputMeeting(List<Long> submittedUserIds, List<Long> pendingUserIds) {
        Meeting meeting = Meeting.create(submittedUserIds.get(0), "테스트 모임", LocalDate.now().plusDays(7), LocalTime.NOON, "test-invite-code");
        for (int i = 1; i < submittedUserIds.size() + pendingUserIds.size(); i++) {
            meeting.incrementParticipantCnt();
        }
        submittedUserIds.forEach(userId -> meeting.incrementSubmittedCnt());
        Long meetingId = meetingRepository.save(meeting).getId();

        for (Long userId : submittedUserIds) {
            Participant participant = Participant.create(meetingId, userId, ParticipantState.COND_PENDING);
            participant.completeCondition();
            participantRepository.save(participant);
        }
        for (Long userId : pendingUserIds) {
            participantRepository.save(Participant.create(meetingId, userId, ParticipantState.COND_PENDING));
        }
        return meetingId;
    }

    private void asUser(Long userId, Runnable task) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        try {
            task.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
