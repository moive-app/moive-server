package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.DateVote;
import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.meeting.service.MeetingLeaveService;
import com.moive.MoiveBE.domain.notification.entity.NotificationType;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteRequest;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


/**
 * [모임 나가기 API] 모임 나가기 후 모임 확정 재판정 테스트
 * - 장소 투표 단계에서 참여자가 나가 남은 투표 가능 참여자가 모두 투표를 마친 상태가 되면 모임이 확정되는지 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vote-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=20",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never"
})
class PlaceVoteLeaveConfirmTest {

    private static final LocalDate TOP_DATE = LocalDate.of(2026, 10, 3);
    private static final LocalTime TOP_TIME = LocalTime.of(18, 0);

    @Autowired private VoteService voteService;
    @Autowired private MeetingLeaveService meetingLeaveService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private DateVoteRepository dateVoteRepository;
    @Autowired private PlaceVoteRepository placeVoteRepository;
    @Autowired private RecommendationRunRepository recommendationRunRepository;
    @Autowired private RecommendedAreaRepository recommendedAreaRepository;
    @Autowired private RecommendedPlaceRepository recommendedPlaceRepository;

    @MockitoBean private GooglePlacesClient googlePlacesClient;
    @MockitoBean private NotificationService notificationService;

    @AfterEach
    void tearDown() {
        placeVoteRepository.deleteAllInBatch();
        dateVoteRepository.deleteAllInBatch();
        recommendedPlaceRepository.deleteAllInBatch();
        recommendedAreaRepository.deleteAllInBatch();
        recommendationRunRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        meetingRepository.deleteAllInBatch();
    }

    @Test
    void 투표하지_않은_마지막_참여자가_나가면_모임이_즉시_확정된다() {
        // given: A, B는 장소 투표 완료, C는 미투표
        Long meetingId = votingMeeting(3);
        Long placeId = recommendedPlace(meetingId);
        voteService.createPlaceVote(1L, meetingId, new PlaceVoteRequest(List.of(placeId)));
        voteService.createPlaceVote(2L, meetingId, new PlaceVoteRequest(List.of(placeId)));
        assertThat(statusOf(meetingId)).isEqualTo(MeetingStatus.VOTING);

        // when: C가 모임을 나감
        asUser(3L, () -> meetingLeaveService.leaveMeeting(meetingId)).run();

        // then: 남은 A, B가 모두 투표한 상태 → 모임 확정 (일정, 장소)
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isEqualTo(placeId);
        assertThat(meeting.getScheduledDate()).isEqualTo(TOP_DATE);
        assertThat(meeting.getScheduledTime()).isEqualTo(TOP_TIME);
    }

    @Test
    void 투표_가능_참여자가_모두_나가_신규_참여자만_남으면_장소_없이_모임이_확정된다() {
        // given: A, B(투표 가능), D(투표 시작 후 입장한 신규 참여자) (A만 장소 투표 완료)
        Long meetingId = votingMeeting(2);
        participantRepository.save(Participant.create(meetingId, 4L, ParticipantState.NEW_RESTRICTED));
        Long placeId = recommendedPlace(meetingId);
        voteService.createPlaceVote(1L, meetingId, new PlaceVoteRequest(List.of(placeId)));

        // when: A, B 모두 나감
        asUser(1L, () -> meetingLeaveService.leaveMeeting(meetingId)).run();
        assertThat(statusOf(meetingId)).isEqualTo(MeetingStatus.VOTING);
        asUser(2L, () -> meetingLeaveService.leaveMeeting(meetingId)).run();

        // then: 더 이상 투표할 사람이 없으므로 모임 확정, 나간 A의 표는 제외되어 유효한 표가 없으므로 장소 null & 확정 알림 x
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isNull();
        verify(notificationService, never())
                .sendNotification(anyLong(), anyLong(), eq(NotificationType.MEETING_CONFIRMED), anyString());
    }

    @Test
    void 장소_추천이_완료되기_전에는_신규_참여자만_남아도_모임을_확정하지_않는다() {
        // given: 투표 단계지만 장소 추천이 아직 완료되지 않은 모임, A(투표 가능) + D(신규 참여자)
        Long meetingId = votingMeeting(1);
        participantRepository.save(Participant.create(meetingId, 4L, ParticipantState.NEW_RESTRICTED));

        // when: A가 나감
        asUser(1L, () -> meetingLeaveService.leaveMeeting(meetingId)).run();

        // then: 장소 투표 단계가 아니므로 확정하지 않음 (추천 완료 후 마감 배치에서 처리 예정)
        assertThat(statusOf(meetingId)).isEqualTo(MeetingStatus.VOTING);
    }

    @Test
    void 이미_확정된_모임에서_참여자가_나가도_다시_확정하지_않는다() {
        // given: A, B 모두 투표해 확정된 모임
        Long meetingId = votingMeeting(2);
        Long placeId = recommendedPlace(meetingId);
        voteService.createPlaceVote(1L, meetingId, new PlaceVoteRequest(List.of(placeId)));
        voteService.createPlaceVote(2L, meetingId, new PlaceVoteRequest(List.of(placeId)));
        assertThat(statusOf(meetingId)).isEqualTo(MeetingStatus.CONFIRMED);
        clearInvocations(notificationService);

        // when: A가 나감 (나간 A의 표를 제외하면 순위가 달라질 수 있는 상황)
        asUser(1L, () -> meetingLeaveService.leaveMeeting(meetingId)).run();

        // then: 확정 결과는 유지, 확정 알림 재발송 x
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isEqualTo(placeId);
        verify(notificationService, never())
                .sendNotification(anyLong(), anyLong(), eq(NotificationType.MEETING_CONFIRMED), anyString());
    }

    private Long votingMeeting(int count) {
        Meeting meeting = Meeting.create(1L, "테스트 모임", null, null, "test-invite-code");
        for (int i = 1; i < count; i++) {
            meeting.incrementParticipantCnt();
        }
        meeting.transitionToVoting();
        Long meetingId = meetingRepository.save(meeting).getId();
        for (long userId = 1; userId <= count; userId++) {
            Long participantId = participantRepository.save(
                    Participant.create(meetingId, userId, ParticipantState.COND_DONE)).getId();
            dateVoteRepository.save(DateVote.create(meetingId, participantId, TOP_DATE, TOP_TIME));
        }
        return meetingId;
    }

    private Long recommendedPlace(Long meetingId) {
        RecommendationRun run = RecommendationRun.create(meetingId);
        run.complete();
        Long runId = recommendationRunRepository.save(run).getId();
        Long areaId = recommendedAreaRepository.save(RecommendedArea.create(runId, "합정역")).getId();
        return recommendedPlaceRepository.save(RecommendedPlace.create(areaId, "google-place-1", "카페", 3)).getId();
    }

    private MeetingStatus statusOf(Long meetingId) {
        return meetingRepository.findById(meetingId).orElseThrow().getStatus();
    }

    private Runnable asUser(Long userId, Runnable task) {
        return () -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            try {
                task.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }
}
