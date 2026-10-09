package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class MeetingRepositoryTest {

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private TestEntityManager em;

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    /**
     * updateStatusByStatusAndScheduledDateBefore (모임 종료 배치)
     */

    @Test
    void 모임_일정이_기준_날짜_이전인_확정_모임만_상태를_변경한다() {
        // given
        Long elapsed = meeting(MeetingStatus.CONFIRMED, TODAY.minusDays(1)); // 어제 일정
        Long today = meeting(MeetingStatus.CONFIRMED, TODAY); // 오늘 일정은 아직 종료 x
        Long voting = meeting(MeetingStatus.VOTING, TODAY.minusDays(1)); // 투표 중인 모임은 대상 x
        Long noSchedule = meeting(MeetingStatus.CONFIRMED, null); // 일정 없이 확정된 모임은 대상 x

        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 10, 0, 0);

        // when
        int updated = meetingRepository.updateStatusByStatusAndScheduledDateBefore(
                MeetingStatus.CONFIRMED, MeetingStatus.COMPLETED, TODAY, updatedAt);
        em.clear();

        // then
        assertThat(updated).isEqualTo(1);
        assertThat(statusOf(elapsed)).isEqualTo(MeetingStatus.COMPLETED);

        // 벌크 UPDATE는 @PreUpdate를 거치지 않으므로 쿼리에서 직접 갱신한 updatedAt이 반영되어야 함
        assertThat(meetingRepository.findById(elapsed).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(statusOf(today)).isEqualTo(MeetingStatus.CONFIRMED);
        assertThat(statusOf(voting)).isEqualTo(MeetingStatus.VOTING);
        assertThat(statusOf(noSchedule)).isEqualTo(MeetingStatus.CONFIRMED);
    }

    private Long meeting(MeetingStatus status, LocalDate scheduledDate) {
        Meeting meeting = Meeting.create(1L, "테스트 모임", scheduledDate,
                scheduledDate != null ? LocalTime.NOON : null, "test-invite-code");
        if (status != MeetingStatus.CONDITION_INPUT) {
            meeting.transitionToVoting();
        }
        if (status == MeetingStatus.CONFIRMED) {
            meeting.confirmPlace(100L);
        }
        Long meetingId = em.persistAndFlush(meeting).getId();
        em.clear();
        return meetingId;
    }

    private MeetingStatus statusOf(Long meetingId) {
        return meetingRepository.findById(meetingId).orElseThrow().getStatus();
    }
}
