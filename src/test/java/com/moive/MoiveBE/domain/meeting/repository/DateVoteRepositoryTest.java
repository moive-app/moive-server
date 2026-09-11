package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.DateVote;
import com.moive.MoiveBE.domain.vote.dto.DateVoteSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class DateVoteRepositoryTest {

    @Autowired
    private DateVoteRepository dateVoteRepository;

    @Autowired
    private TestEntityManager em;

    private static final Long MEETING_ID = 1L;
    private static final Long OTHER_MEETING_ID = 99L;

    // 참여자 id
    private static final Long A = 10L;
    private static final Long B = 20L;
    private static final Long C = 30L;
    private static final Long D = 40L;
    private static final Long E = 50L;

    @Test
    void 서로_다른_참여자가_투표하면_참여자_수를_센다() {
        vote(A, "2026-09-12", "13:00");
        vote(B, "2026-09-13", "14:00");
        vote(C, "2026-09-13", "10:00");

        assertThat(dateVoteRepository.countDistinctVoters(MEETING_ID)).isEqualTo(3);
    }

    @Test
    void 같은_참여자가_여러_날짜에_투표해도_1명으로_센다() {
        vote(A, "2026-09-11", "12:00");
        vote(A, "2026-09-12", "15:00");
        vote(A, "2026-09-15", "12:00");

        assertThat(dateVoteRepository.countDistinctVoters(MEETING_ID)).isEqualTo(1);
    }

    @Test
    void 날짜나_시간이_null인_투표는_참여자_수에서_제외한다() {
        vote(A, "2026-09-12", "13:00");
        voteRaw(B, null, LocalTime.of(14, 0));
        voteRaw(C, LocalDate.of(2026, 9, 13), null);

        assertThat(dateVoteRepository.countDistinctVoters(MEETING_ID)).isEqualTo(1);
    }

    @Test
    void 투표가_없으면_참여자_수는_0() {
        assertThat(dateVoteRepository.countDistinctVoters(MEETING_ID)).isZero();
    }

    @Test
    void Top3_날짜_대표시간_득표수가_일치한다() {
        // A)
        vote(A, "2026-09-12", "13:00");
        vote(A, "2026-09-15", "15:00");
        // B)
        vote(B, "2026-09-13", "14:00");
        vote(B, "2026-09-15", "17:00");
        // C)
        vote(C, "2026-09-13", "10:00");
        vote(C, "2026-09-15", "18:00");
        // D)
        vote(D, "2026-09-11", "12:00");
        vote(D, "2026-09-12", "15:00");
        vote(D, "2026-09-14", "12:00");

        List<DateVoteSummary> result = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3));

        assertThat(result).hasSize(3);
        assertCandidate(result.get(0), "2026-09-15", "18:00", 3);
        assertCandidate(result.get(1), "2026-09-12", "15:00", 2);
        assertCandidate(result.get(2), "2026-09-13", "14:00", 2);
    }

    @Test
    void 득표가_동률이면_더_이른_날짜가_앞선다() {
        // 9/10, 9/20, 9/30 모두 2표로 동일
        vote(A, "2026-09-30", "10:00");
        vote(B, "2026-09-30", "11:00");
        vote(A, "2026-09-20", "10:00");
        vote(B, "2026-09-20", "11:00");
        vote(C, "2026-09-10", "10:00");
        vote(D, "2026-09-10", "11:00");

        List<DateVoteSummary> result = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3));

        assertThat(result).extracting(DateVoteSummary::candidateDate)
                .containsExactly(
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 20),
                        LocalDate.of(2026, 9, 30)
                );
    }

    @Test
    void 득표수가_높은_날짜가_동률_날짜보다_먼저_온다() {
        // 9/15: 3표, 9/12: 2표, 9/13: 2표
        vote(A, "2026-09-15", "10:00");
        vote(B, "2026-09-15", "10:00");
        vote(C, "2026-09-15", "10:00");
        vote(A, "2026-09-13", "10:00");
        vote(B, "2026-09-13", "10:00");
        vote(A, "2026-09-12", "10:00");
        vote(B, "2026-09-12", "10:00");

        List<DateVoteSummary> result = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3));

        assertThat(result).extracting(DateVoteSummary::candidateDate, DateVoteSummary::voterCnt)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(LocalDate.of(2026, 9, 15), 3L),
                        org.assertj.core.api.Assertions.tuple(LocalDate.of(2026, 9, 12), 2L),
                        org.assertj.core.api.Assertions.tuple(LocalDate.of(2026, 9, 13), 2L)
                );
    }

    @Test
    void 후보가_요청_개수보다_적으면_있는_만큼만_반환한다() {
        vote(A, "2026-09-12", "13:00");
        vote(B, "2026-09-13", "14:00");

        assertThat(dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3))).hasSize(2);
    }

    @Test
    void 후보가_요청_개수보다_많으면_상위_N개만_반환한다() {
        vote(A, "2026-09-10", "10:00");
        vote(B, "2026-09-11", "10:00");
        vote(C, "2026-09-12", "10:00");
        vote(D, "2026-09-13", "10:00");
        vote(E, "2026-09-14", "10:00");

        List<DateVoteSummary> result = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3));

        // 전부 1표라 날짜 오름차순으로 앞의 3개
        assertThat(result).extracting(DateVoteSummary::candidateDate).containsExactly(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 12)
        );
    }

    @Test
    void 한_날짜의_여러_시간_투표_중_가장_늦은_시간이_대표_시간이_되고_득표수는_그_날짜_투표자_수다() {
        vote(A, "2026-09-15", "10:00");
        vote(B, "2026-09-15", "18:00");
        vote(C, "2026-09-15", "12:00");

        DateVoteSummary summary = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3)).get(0);

        assertThat(summary.candidateTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(summary.voterCnt()).isEqualTo(3L);
    }

    @Test
    void 같은_참여자가_같은_날짜에_중복_투표해도_득표수는_1이고_늦은_시간이_대표가_된다() {
        vote(A, "2026-09-20", "10:00");
        vote(A, "2026-09-20", "14:00");

        DateVoteSummary summary = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3)).get(0);

        assertThat(summary.voterCnt()).isEqualTo(1L);
        assertThat(summary.candidateTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void votedByMe는_해당_참여자가_그_날짜에_투표했는지를_나타낸다() {
        vote(A, "2026-09-15", "18:00");
        vote(B, "2026-09-15", "17:00");
        vote(B, "2026-09-13", "14:00");
        vote(C, "2026-09-13", "10:00");

        List<DateVoteSummary> asA = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3));
        assertThat(pick(asA, "2026-09-15").isVotedByMe()).isTrue();
        assertThat(pick(asA, "2026-09-13").isVotedByMe()).isFalse();

        List<DateVoteSummary> asC = dateVoteRepository.aggregateTopDates(MEETING_ID, C, top(3));
        assertThat(pick(asC, "2026-09-15").isVotedByMe()).isFalse();
        assertThat(pick(asC, "2026-09-13").isVotedByMe()).isTrue();
    }

    @Test
    void 조회한_참여자가_아무_날짜에도_투표하지_않았으면_votedByMe는_모두_false다() {
        vote(A, "2026-09-15", "18:00");
        vote(B, "2026-09-13", "14:00");

        // 투표한 적 없는 참여자 id 로 조회
        List<DateVoteSummary> result = dateVoteRepository.aggregateTopDates(MEETING_ID, 999L, top(3));

        assertThat(result).allSatisfy(s -> assertThat(s.isVotedByMe()).isFalse());
    }

    @Test
    void 날짜나_시간이_null인_투표는_후보로_집계되지_않는다() {
        vote(A, "2026-09-12", "13:00");
        voteRaw(B, null, LocalTime.of(14, 0));
        voteRaw(C, LocalDate.of(2026, 9, 13), null);

        List<DateVoteSummary> result = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3));

        assertThat(result).hasSize(1);
        assertCandidate(result.get(0), "2026-09-12", "13:00", 1);
    }

    @Test
    void 투표가_없으면_빈_리스트를_반환한다() {
        assertThat(dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3))).isEmpty();
    }

    @Test
    void 다른_모임의_투표는_집계에_포함되지_않는다() {
        vote(A, "2026-09-12", "13:00");
        voteFor(OTHER_MEETING_ID, B, "2026-09-12", "13:00");
        voteFor(OTHER_MEETING_ID, C, "2026-09-13", "10:00");

        List<DateVoteSummary> result = dateVoteRepository.aggregateTopDates(MEETING_ID, A, top(3));

        assertThat(result).hasSize(1);
        assertCandidate(result.get(0), "2026-09-12", "13:00", 1);
    }

    /**
     * helpers
     */

    private void vote(Long participantId, String date, String time) {
        voteFor(MEETING_ID, participantId, date, time);
    }

    private void voteFor(Long meetingId, Long participantId, String date, String time) {
        voteRawFor(meetingId, participantId, LocalDate.parse(date), LocalTime.parse(time));
    }

    private void voteRaw(Long participantId, LocalDate date, LocalTime time) {
        voteRawFor(MEETING_ID, participantId, date, time);
    }

    private void voteRawFor(Long meetingId, Long participantId, LocalDate date, LocalTime time) {
        em.persistAndFlush(DateVote.create(meetingId, participantId, date, time));
    }

    private PageRequest top(int n) {
        return PageRequest.of(0, n);
    }

    private DateVoteSummary pick(List<DateVoteSummary> list, String date) {
        LocalDate target = LocalDate.parse(date);
        return list.stream()
                .filter(s -> s.candidateDate().equals(target))
                .findFirst()
                .orElseThrow(() -> new AssertionError("후보에 " + date + " 없음: " + list));
    }

    private void assertCandidate(DateVoteSummary summary, String date, String time, long voterCnt) {
        assertThat(summary.candidateDate()).isEqualTo(LocalDate.parse(date));
        assertThat(summary.candidateTime()).isEqualTo(LocalTime.parse(time));
        assertThat(summary.voterCnt()).isEqualTo(voterCnt);
    }
}
