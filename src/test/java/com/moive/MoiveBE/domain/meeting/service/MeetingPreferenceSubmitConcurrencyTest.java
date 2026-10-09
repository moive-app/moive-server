package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.SubmitPreferenceRequest;
import com.moive.MoiveBE.domain.meeting.entity.ActivityType;
import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.ActivityRepository;
import com.moive.MoiveBE.domain.meeting.repository.DateVoteRepository;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.meeting.repository.PreferenceActivityRepository;
import com.moive.MoiveBE.domain.notification.service.NotificationService;
import com.moive.MoiveBE.domain.recommendation.service.AreaRecommendationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * [조건 제출 API] 동시성 테스트
 * - 여러 참여자가 동시에 조건을 제출해도 제출 인원이 정확히 집계되고, VOTING 전환 및 장소 추천이 한 번만 일어나는지 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:preference-concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=20",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never"
})
class MeetingPreferenceSubmitConcurrencyTest {

    private static final String INVITE_CODE = "test-invite-code";

    @Autowired private MeetingService meetingService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private ParticipantPreferenceRepository participantPreferenceRepository;
    @Autowired private PreferenceActivityRepository preferenceActivityRepository;
    @Autowired private DateVoteRepository dateVoteRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AreaRecommendationService areaRecommendationService;
    @MockitoBean private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("insert into activities (name) values (?)", ActivityType.CAFE_DESSERT.name());
    }

    @AfterEach
    void tearDown() {
        preferenceActivityRepository.deleteAllInBatch();
        participantPreferenceRepository.deleteAllInBatch();
        dateVoteRepository.deleteAllInBatch();
        participantRepository.deleteAllInBatch();
        meetingRepository.deleteAllInBatch();
        activityRepository.deleteAllInBatch();
    }

    @Test
    void 참여자_전원이_동시에_조건을_제출해도_제출_인원이_정확하고_장소_추천은_한_번만_시작된다() throws Exception {
        // given: 참여자 5명 모두 조건 미제출
        int participantCnt = 5;
        Long meetingId = conditionInputMeetingWithParticipants(participantCnt);

        // when: 5명이 동시에 조건 제출
        List<Future<?>> results = submitConcurrently(meetingId, List.of(1L, 2L, 3L, 4L, 5L));
        for (Future<?> result : results) {
            result.get(10, TimeUnit.SECONDS);
        }

        // then: 제출 인원: 5명, 모임: VOTING, 장소 추천: 한 번만 시작
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getSubmittedCnt()).isEqualTo(participantCnt);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.VOTING);
        verify(areaRecommendationService, times(1)).recommend(meetingId);

        assertThat(participantRepository.findAllByMeetingIdAndLeftAtIsNull(meetingId))
                .extracting(Participant::getState)
                .containsOnly(ParticipantState.COND_DONE);
        assertThat(participantPreferenceRepository.count()).isEqualTo(participantCnt);
    }

    @Test
    void 마지막_제출자가_조건을_동시에_두_번_제출해도_제출_인원은_한_번만_증가하고_장소_추천은_한_번만_시작된다() throws Exception {
        // given: 참여자 3명 중 2명은 조건 제출 완료, 3번 유저만 미제출
        Long meetingId = conditionInputMeetingWithParticipants(3);
        submitConcurrently(meetingId, List.of(1L, 2L)).forEach(this::await);

        // when: 3번 유저가 같은 조건을 동시에 두 번 제출
        List<Future<?>> results = submitConcurrently(meetingId, List.of(3L, 3L));
        long succeeded = results.stream().filter(this::succeeded).count();

        // then: 한 요청만 반영 (제출 인원: 3명, 3번 유저의 선호 조건: 1건, 장소 추천: 한 번만 시작)
        assertThat(succeeded).isEqualTo(1);
        Meeting meeting = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(meeting.getSubmittedCnt()).isEqualTo(3);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.VOTING);
        verify(areaRecommendationService, times(1)).recommend(meetingId);
        assertThat(participantPreferenceRepository.count()).isEqualTo(3);
    }

    private Long conditionInputMeetingWithParticipants(int count) {
        Meeting meeting = Meeting.create(1L, "테스트 모임", LocalDate.now().plusDays(7), LocalTime.NOON, INVITE_CODE);
        for (int i = 1; i < count; i++) {
            meeting.incrementParticipantCnt();
        }
        Long meetingId = meetingRepository.save(meeting).getId();
        for (long userId = 1; userId <= count; userId++) {
            participantRepository.save(Participant.create(meetingId, userId, ParticipantState.COND_PENDING));
        }
        return meetingId;
    }

    private List<Future<?>> submitConcurrently(Long meetingId, List<Long> userIds) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(userIds.size());
        CountDownLatch ready = new CountDownLatch(userIds.size());
        CountDownLatch start = new CountDownLatch(1);

        List<Future<?>> results = new ArrayList<>();
        for (Long userId : userIds) {
            results.add(executor.submit(asUser(userId, () -> {
                ready.countDown();
                awaitLatch(start);
                meetingService.submitPreference(meetingId, preferenceRequest());
            })));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        return results;
    }

    private SubmitPreferenceRequest preferenceRequest() {
        return new SubmitPreferenceRequest(
                null,
                "합정역",
                new BigDecimal("37.5540203935757"),
                new BigDecimal("126.916389124374"),
                60,
                List.of(ActivityType.CAFE_DESSERT)
        );
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

    private void awaitLatch(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void await(Future<?> result) {
        try {
            result.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean succeeded(Future<?> result) {
        try {
            result.get(10, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
