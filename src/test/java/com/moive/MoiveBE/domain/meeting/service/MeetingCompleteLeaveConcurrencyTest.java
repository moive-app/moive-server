package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.meeting.repository.MeetingRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.vote.service.VoteService;
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
import static org.mockito.Mockito.doAnswer;

/**
 * [모임 종료 배치 ↔ 모임 나가기 API] 동시성 테스트
 * - '모임 나가기' 커밋 전 모임 종료 배치가 실행될 때, '모임 나가기'의 변경 사항이 덮어써지지 않는지 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:meeting-complete;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never"
})
class MeetingCompleteLeaveConcurrencyTest {

    private static final long PAUSE_MILLIS = 300;

    @Autowired private MeetingService meetingService;
    @Autowired private MeetingLeaveService meetingLeaveService;
    @Autowired private MeetingRepository meetingRepository;
    @Autowired private ParticipantRepository participantRepository;

    @MockitoBean private VoteService voteService;

    @AfterEach
    void tearDown() {
        participantRepository.deleteAllInBatch();
        meetingRepository.deleteAllInBatch();
    }

    @Test
    void 모임_나가기가_커밋되기_전에_종료_배치가_실행돼도_나가기의_변경이_유지된다() throws Exception {
        // given: 일정이 어제인 확정 모임, 모임장 A(userId 1) + B(userId 2)
        Meeting meeting = Meeting.create(1L, "테스트 모임", LocalDate.now().minusDays(1), LocalTime.NOON, "test-invite-code");
        meeting.incrementParticipantCnt();
        meeting.transitionToVoting();
        meeting.confirmPlace(100L);
        Long meetingId = meetingRepository.save(meeting).getId();
        participantRepository.save(Participant.create(meetingId, 1L, ParticipantState.CONFIRMED));
        participantRepository.save(Participant.create(meetingId, 2L, ParticipantState.CONFIRMED));

        CountDownLatch leaving = new CountDownLatch(1);
        doAnswer(invocation -> {
            leaving.countDown();
            Thread.sleep(PAUSE_MILLIS);
            return null;
        }).when(voteService).confirmMeetingIfAllVoted(any());

        // when: 모임장 A가 나가는 중(인원 감소 + 모임장 B로 승계, 커밋 전)에 종료 배치 실행
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> leave = executor.submit(() -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
            try {
                meetingLeaveService.leaveMeeting(meetingId);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        assertThat(leaving.await(5, TimeUnit.SECONDS)).isTrue(); // 모임 나가기가 '모임 수정 후~커밋 전' 상태가 될 때까지 대기
        Future<?> complete = executor.submit(() -> meetingService.completeElapsedMeetings());
        leave.get(10, TimeUnit.SECONDS);
        complete.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 모임은 종료되고, 나가기의 변경(인원 감소, 모임장 승계)도 유지됨
        Meeting result = meetingRepository.findById(meetingId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(MeetingStatus.COMPLETED);
        assertThat(result.getParticipantCnt()).isEqualTo(1);
        assertThat(result.getCreatorUserId()).isEqualTo(2L);
    }
}
