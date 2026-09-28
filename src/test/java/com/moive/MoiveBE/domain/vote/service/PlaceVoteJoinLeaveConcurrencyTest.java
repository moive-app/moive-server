package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.meeting.service.MeetingLeaveService;
import com.moive.MoiveBE.domain.meeting.service.MeetingService;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteRequest;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * [장소 투표 확정 ↔ 모임 참여/모임 나가기 API] 동시성 테스트
 * - 마지막 장소 투표가 장소 확정을 커밋하기 전 모임 참여/모임 나가기 요청이 들어올 경우 확정 결과가 덮어써지지 않는지 검증
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
class PlaceVoteJoinLeaveConcurrencyTest {

    private static final String INVITE_CODE = "test-invite-code";
    private static final long PAUSE_MILLIS = 300;

    @Autowired private VoteService voteService;
    @Autowired private MeetingLeaveService meetingLeaveService;
    @Autowired private MeetingService meetingService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private PlaceVoteRepository placeVoteRepository;
    @Autowired private RecommendationRunRepository recommendationRunRepository;
    @Autowired private RecommendedAreaRepository recommendedAreaRepository;
    @Autowired private RecommendedPlaceRepository recommendedPlaceRepository;

    @MockitoBean private GooglePlacesClient googlePlacesClient;
    @MockitoBean private NotificationService notificationService;

    @AfterEach
    void tearDown() {
        placeVoteRepository.deleteAllInBatch();
        recommendedPlaceRepository.deleteAllInBatch();
        recommendedAreaRepository.deleteAllInBatch();
        recommendationRunRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        meetingRepository.deleteAllInBatch();
    }

    @Test
    void 마지막_투표로_확정되는_중에_투표한_참여자가_나가도_확정이_유지된다() throws Exception {
        // given: A, B는 투표 완료, C가 마지막 투표자
        Long meetingId = votingMeetingWithParticipants(3);
        Long placeId = recommendedPlace(meetingId);
        Long leavingParticipantId = participantIdOf(meetingId, 2L);
        vote(meetingId, participantIdOf(meetingId, 1L), placeId);
        vote(meetingId, leavingParticipantId, placeId);
        int participantCntBefore = meetingRepository.findById(meetingId).orElseThrow().getParticipantCnt();

        CountDownLatch confirming = pauseWhenConfirmNotificationSent();

        // when: C의 마지막 투표로 장소 확정을 커밋하기 전에 B가 모임을 나감
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> lastVote = executor.submit(asUser(3L,
                () -> voteService.createPlaceVote(3L, meetingId, new PlaceVoteRequest(List.of(placeId)))));
        assertThat(confirming.await(5, TimeUnit.SECONDS)).isTrue(); // 마지막 투표가 '장소 확정 후 ~ 커밋 전' 상태가 될 때까지 대기
        Future<?> leave = executor.submit(asUser(2L, () -> meetingLeaveService.leaveMeeting(meetingId)));
        lastVote.get(10, TimeUnit.SECONDS);
        leave.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 장소 확정은 유지되고, 모임 나가기 처리(인원 감소, leftAt)도 함께 반영됨
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isEqualTo(placeId);

        assertThat(meeting.getParticipantCnt()).isEqualTo(participantCntBefore - 1);
        Participant leftParticipant = participantRepository.findById(leavingParticipantId).orElseThrow();
        assertThat(leftParticipant.getLeftAt()).isNotNull();
    }

    @Test
    void 마지막_투표로_확정되는_중에_새로운_참여자가_입장해도_확정이_유지된다() throws Exception {
        // given: A, B는 투표 완료, C가 마지막 투표자
        Long meetingId = votingMeetingWithParticipants(3);
        Long placeId = recommendedPlace(meetingId);
        vote(meetingId, participantIdOf(meetingId, 1L), placeId);
        vote(meetingId, participantIdOf(meetingId, 2L), placeId);
        int participantCntBefore = meetingRepository.findById(meetingId).orElseThrow().getParticipantCnt();

        CountDownLatch confirming = pauseWhenConfirmNotificationSent();

        // when: C가 마지막으로 투표하며 장소 확정 커밋이 이뤄지기 전, D가 모임 참여
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> lastVote = executor.submit(asUser(3L,
                () -> voteService.createPlaceVote(3L, meetingId, new PlaceVoteRequest(List.of(placeId)))));
        assertThat(confirming.await(5, TimeUnit.SECONDS)).isTrue(); // 마지막 투표가 "확정 후, 커밋 전" 상태가 될 때까지 대기
        Future<?> join = executor.submit(asUser(4L, () -> meetingService.joinMeeting(INVITE_CODE)));
        lastVote.get(10, TimeUnit.SECONDS);
        join.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 장소 확정은 유지되고, 모임 참여 처리(인원 증가, 신규 참여자)도 함께 반영됨
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isEqualTo(placeId);

        assertThat(meeting.getParticipantCnt()).isEqualTo(participantCntBefore + 1);
        assertThat(participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, 4L))
                .get()
                .extracting(Participant::getState)
                .isEqualTo(ParticipantState.NEW_RESTRICTED);
    }

    private Long votingMeetingWithParticipants(int count) {
        Meeting meeting = Meeting.create(1L, "테스트 모임", LocalDate.now().plusDays(7), LocalTime.NOON, INVITE_CODE);
        for (int i = 1; i < count; i++) {
            meeting.incrementParticipantCnt();
        }
        meeting.transitionToVoting();
        Long meetingId = meetingRepository.save(meeting).getId();
        for (long userId = 1; userId <= count; userId++) {
            participantRepository.save(Participant.create(meetingId, userId, ParticipantState.COND_DONE));
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

    private Long participantIdOf(Long meetingId, Long userId) {
        return participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId).orElseThrow().getId();
    }

    private void vote(Long meetingId, Long participantId, Long placeId) {
        placeVoteRepository.save(PlaceVote.create(meetingId, participantId, placeId));
    }

    private CountDownLatch pauseWhenConfirmNotificationSent() {
        CountDownLatch confirming = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (confirming.getCount() > 0) {
                confirming.countDown();
                Thread.sleep(PAUSE_MILLIS);
            }
            return null;
        }).when(notificationService).sendNotification(anyLong(), anyLong(), any(), anyString());
        return confirming;
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
