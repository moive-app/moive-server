package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.DateVote;
import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [장소 투표 생성 API] 모임 확정(일정,장소) 테스트
 * - 모임 생성 시 일정 미정인 모임에서 마지막 장소 투표로 모임이 확정될 때, 일정(일정 투표 집계 1위)과 장소(장소 투표 득표 1위)가 함께 확정되는지 검증
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
class PlaceVoteMeetingConfirmTest {

    private static final LocalDate TOP_DATE = LocalDate.of(2026, 10, 3);
    private static final LocalTime TOP_TIME = LocalTime.of(18, 0);

    @Autowired private VoteService voteService;
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
    void 마지막으로_장소_투표하면_모임_일정과_장소가_함께_확정된다() {
        // given: 일정 미정 모임, 조건 입력(일정 투표) 완료
        Long meetingId = votingMeetingWithoutSchedule(2);

        List<Long> placeIds = recommendedPlaces(meetingId, 2);
        Long topPlaceId = placeIds.get(0);
        Long otherPlaceId = placeIds.get(1);

        LocalDate otherDate = TOP_DATE.plusDays(1);

        dateVoteRepository.save(DateVote.create(meetingId, participantIdOf(meetingId, 1L), TOP_DATE, LocalTime.of(12, 0)));
        dateVoteRepository.save(DateVote.create(meetingId, participantIdOf(meetingId, 2L), TOP_DATE, TOP_TIME));
        dateVoteRepository.save(DateVote.create(meetingId, participantIdOf(meetingId, 2L), otherDate, TOP_TIME));

        // when: A는 두 장소 모두, B는 첫 번째 장소에 투표 (B가 마지막 투표자)
        voteService.createPlaceVote(1L, meetingId, new PlaceVoteRequest(List.of(topPlaceId, otherPlaceId)));
        Meeting afterFirstVote = meetingRepository.findById(meetingId).orElseThrow();
        voteService.createPlaceVote(2L, meetingId, new PlaceVoteRequest(List.of(topPlaceId)));

        // then: 첫 투표 후에는 아직 미확정 (일정, 장소 모두 비어 있음)
        assertThat(afterFirstVote.getStatus()).isEqualTo(MeetingStatus.VOTING);
        assertThat(afterFirstVote.getScheduledDate()).isNull();
        assertThat(afterFirstVote.getConfirmedPlaceId()).isNull();

        // 마지막 투표로 모임 확정
        // - 장소: 득표 1위(topPlaceId: 2표, otherPlaceId: 1표)
        // - 일정: 집계 1위 날짜(TOP_DATE: 2명) + 그 날짜의 가장 늦은 시간(TOP_TIME)
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isEqualTo(topPlaceId);
        assertThat(meeting.getScheduledDate()).isEqualTo(TOP_DATE);
        assertThat(meeting.getScheduledTime()).isEqualTo(TOP_TIME);
    }

    private Long votingMeetingWithoutSchedule(int count) {
        Meeting meeting = Meeting.create(1L, "테스트 모임", null, null, "test-invite-code");
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

    private List<Long> recommendedPlaces(Long meetingId, int count) {
        RecommendationRun run = RecommendationRun.create(meetingId);
        run.complete();
        Long runId = recommendationRunRepository.save(run).getId();
        Long areaId = recommendedAreaRepository.save(RecommendedArea.create(runId, "합정역")).getId();
        List<Long> placeIds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            placeIds.add(recommendedPlaceRepository.save(
                    RecommendedPlace.create(areaId, "google-place-" + i, "카페", 3)).getId());
        }
        return placeIds;
    }

    private Long participantIdOf(Long meetingId, Long userId) {
        return participantRepository.findByMeetingIdAndUserIdAndLeftAtIsNull(meetingId, userId).orElseThrow().getId();
    }
}
