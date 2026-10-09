package com.moive.MoiveBE.domain.vote.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
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
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import com.moive.MoiveBE.domain.vote.repository.PlaceVoteRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * [장소 투표 생성 API] 동시성 테스트
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
class PlaceVoteServiceConcurrencyTest {

    private static final int VOTER_CNT = 10;

    @Autowired private VoteService voteService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private PlaceVoteRepository placeVoteRepository;
    @Autowired private RecommendationRunRepository recommendationRunRepository;
    @Autowired private RecommendedAreaRepository recommendedAreaRepository;
    @Autowired private RecommendedPlaceRepository recommendedPlaceRepository;

    @MockitoBean private GooglePlacesClient googlePlacesClient;
    @MockitoBean private NotificationService notificationService;

    private Long meetingId;
    private List<Participant> participants;
    private List<Long> placeIds;

    @BeforeEach
    void setUp() {
        Meeting meeting = Meeting.create(1L, "장소 투표 동시성 테스트용 모임", LocalDate.now().plusDays(7), LocalTime.NOON, "test-invite-code");
        meeting.transitionToVoting();
        meetingId = meetingRepository.save(meeting).getId();

        participants = new ArrayList<>();
        for (long userId = 1; userId <= VOTER_CNT; userId++) {
            participants.add(participantRepository.save(
                    Participant.create(meetingId, userId, ParticipantState.COND_DONE)));
        }

        RecommendationRun run = RecommendationRun.create(meetingId);
        run.complete();
        Long runId = recommendationRunRepository.save(run).getId();
        Long areaId = recommendedAreaRepository.save(RecommendedArea.create(runId, "합정역")).getId();
        placeIds = List.of(
                recommendedPlaceRepository.save(RecommendedPlace.create(areaId, "google-place-1", "카페", 3)).getId(),
                recommendedPlaceRepository.save(RecommendedPlace.create(areaId, "google-place-2", "레스토랑", 2)).getId(),
                recommendedPlaceRepository.save(RecommendedPlace.create(areaId, "google-place-3", "영화관", 1)).getId()
        );
    }

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
    void 전원이_동시에_투표해도_마지막_투표에서_장소가_한_번만_확정된다() throws InterruptedException {
        // given: 투표 가능 인원 10명이 한 명도 투표하지 않은 상태 가정

        // when: 10명이 동시에 투표
        List<Runnable> tasks = participants.stream()
                .<Runnable>map(p -> () -> voteService.createPlaceVote(
                        p.getUserId(), meetingId, new PlaceVoteRequest(List.of(placeIds.get(0)))))
                .toList();
        Result result = runConcurrently(tasks);

        // then: 모든 투표 성공 + 모임 장소 확정 + 참여자당 1회 확정 알림
        assertThat(result.successCnt()).isEqualTo(VOTER_CNT);
        assertThat(placeVoteRepository.countDistinctVoters(meetingId)).isEqualTo(VOTER_CNT);

        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(meeting.getConfirmedPlaceId()).isEqualTo(placeIds.get(0));

        assertThat(participantRepository.findAllByMeetingIdAndLeftAtIsNull(meetingId))
                .extracting(Participant::getState)
                .containsOnly(ParticipantState.CONFIRMED);
        verify(notificationService, times(VOTER_CNT))
                .sendNotification(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void 같은_참여자가_서로_다른_장소로_동시에_여러_번_요청해도_한_번만_저장된다() throws InterruptedException {
        // given: 같은 참여자가 서로 다른 장소 조합으로 5번 동시 요청
        Participant me = participants.get(0);
        List<List<Long>> requests = List.of(
                List.of(placeIds.get(0)),
                List.of(placeIds.get(1)),
                List.of(placeIds.get(2)),
                List.of(placeIds.get(0), placeIds.get(1)),
                List.of(placeIds.get(1), placeIds.get(2))
        );

        // when
        List<Runnable> tasks = requests.stream()
                .<Runnable>map(ids -> () -> voteService.createPlaceVote(
                        me.getUserId(), meetingId, new PlaceVoteRequest(ids)))
                .toList();
        Result result = runConcurrently(tasks);

        // then: 1건만 성공, 나머지는 기투표 예외 발생
        assertThat(result.successCnt()).isEqualTo(1);
        assertThat(result.errorCnt(CustomErrorCode.PLACE_VOTE_ALREADY_DONE)).isEqualTo(requests.size() - 1);

        List<PlaceVote> myVotes = placeVoteRepository.findAll().stream()
                .filter(v -> v.getParticipantId().equals(me.getId()))
                .toList();
        assertThat(requests).anySatisfy(ids ->
                assertThat(myVotes).extracting(PlaceVote::getRecommendedPlaceId)
                        .containsExactlyInAnyOrderElementsOf(ids));
    }

    // 모든 작업 동시 실행 후 성공 수/에러코드별 실패 수 집계
    private Result runConcurrently(List<Runnable> tasks) throws InterruptedException {
        int threadCnt = tasks.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCnt);
        CountDownLatch ready = new CountDownLatch(threadCnt);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCnt);

        AtomicInteger successCnt = new AtomicInteger();
        Map<CustomErrorCode, AtomicInteger> errorCnts = new ConcurrentHashMap<>();
        List<Throwable> unexpected = new ArrayList<>();

        for (Runnable task : tasks) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.run();
                    successCnt.incrementAndGet();
                } catch (CustomException e) {
                    errorCnts.computeIfAbsent(e.getCustomErrorCode(), k -> new AtomicInteger()).incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpected) {
                        unexpected.add(t);
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        assertThat(unexpected).isEmpty();
        return new Result(successCnt.get(), errorCnts);
    }

    private record Result(int successCnt, Map<CustomErrorCode, AtomicInteger> errorCnts) {
        int errorCnt(CustomErrorCode code) {
            AtomicInteger cnt = errorCnts.get(code);
            return cnt == null ? 0 : cnt.get();
        }
    }
}
