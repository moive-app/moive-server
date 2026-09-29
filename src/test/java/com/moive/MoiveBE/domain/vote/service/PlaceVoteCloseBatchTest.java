package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.DateVote;
import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.notification.entity.NotificationType;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendationRunRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedAreaRepository;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

/**
 * [장소 투표 마감 배치] 통합 테스트
 * - PlaceVoteCloseService.finalizeExpiredPlaceVotes() → 모임별 트랜잭션(VoteService.finalizeExpiredPlaceVote) 연결 시
 *   모임마다 따로 커밋 및 롤백되는지 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:place-vote-close;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never",
        "place-vote.deadline-days=0" //마감 대상으로 설정
})
class PlaceVoteCloseBatchTest {

    private static final LocalDate TOP_DATE = LocalDate.of(2026, 10, 3);
    private static final LocalTime TOP_TIME = LocalTime.of(18, 0);

    @Autowired private PlaceVoteCloseService placeVoteCloseService;
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
    void 마감_배치에서_한_모임_처리가_실패해도_다른_모임의_확정은_커밋되고_실패한_모임만_롤백된다() {
        // given
        // - A, B, C: 장소 추천 완료 + 장소 투표 1건 → 마감 대상 o
        // - B: 확정의 마지막 단계(확정 알림 발송)에서 예외 발생
        // - D: 장소 추천 미완료 → 마감 대상 x
        Long meetingA = votingMeetingWithOneVote(true);
        Long meetingB = votingMeetingWithOneVote(true);
        Long meetingC = votingMeetingWithOneVote(true);
        Long meetingD = votingMeetingWithOneVote(false);
        doThrow(new IllegalStateException("확정 알림 저장 실패"))
                .when(notificationService)
                .sendNotification(anyLong(), eq(meetingB), eq(NotificationType.MEETING_CONFIRMED), anyString());

        // when: 스케줄러가 호출하는 마감 배치 실행
        assertThatCode(() -> placeVoteCloseService.finalizeExpiredPlaceVotes()).doesNotThrowAnyException();

        // then
        // - A, C: 모임 확정 커밋됨 (B의 실패와 무관)
        assertConfirmed(meetingA);
        assertConfirmed(meetingC);

        // - B: 예외 전에 수행한 모임 확정까지 모두 롤백 → 다음 실행에서 다시 대상이 됨
        Meeting b = meetingRepository.findById(meetingB).orElseThrow();
        assertThat(b.getStatus()).isEqualTo(MeetingStatus.VOTING);
        assertThat(b.getConfirmedPlaceId()).isNull();
        assertThat(b.getScheduledDate()).isNull();

        // - D: 장소 투표 단계가 아니므로 처리하지 않음
        assertThat(meetingRepository.findById(meetingD).orElseThrow().getStatus()).isEqualTo(MeetingStatus.VOTING);
    }

    private void assertConfirmed(Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isNotNull();
        assertThat(meeting.getScheduledDate()).isEqualTo(TOP_DATE);
        assertThat(meeting.getScheduledTime()).isEqualTo(TOP_TIME);
    }

    private Long votingMeetingWithOneVote(boolean recommendationCompleted) {
        Meeting meeting = Meeting.create(1L, "테스트 모임", null, null, "test-invite-code");
        meeting.transitionToVoting();
        Long meetingId = meetingRepository.save(meeting).getId();

        Long participantId = participantRepository.save(
                Participant.create(meetingId, 1L, ParticipantState.VOTE_DONE)).getId();
        dateVoteRepository.save(DateVote.create(meetingId, participantId, TOP_DATE, TOP_TIME));

        RecommendationRun run = RecommendationRun.create(meetingId);
        if (recommendationCompleted) {
            run.complete();
        }
        Long runId = recommendationRunRepository.save(run).getId();
        Long areaId = recommendedAreaRepository.save(RecommendedArea.create(runId, "합정역")).getId();
        Long placeId = recommendedPlaceRepository.save(
                RecommendedPlace.create(areaId, "google-place-" + meetingId, "카페", 3)).getId();

        placeVoteRepository.save(PlaceVote.create(meetingId, participantId, placeId));

        return meetingId;
    }
}
