package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantState;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
class ParticipantRepositoryTest {

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private TestEntityManager em;

    private static final Long MEETING_ID = 1L;
    private static final Long OTHER_MEETING_ID = 99L;
    private static final Long PLACE_ID = 1000L;

    /**
     * countParticipantsYetToVote
     */

    @Test
    void 투표_가능_참여자_중_투표하지_않은_인원만_센다() {
        Long a = participant(MEETING_ID, 1L, ParticipantState.COND_DONE);

        participant(MEETING_ID, 2L, ParticipantState.COND_DONE);
        participant(MEETING_ID, 3L, ParticipantState.COND_DONE);
        vote(MEETING_ID, a);

        assertThat(countParticipantsYetToVote()).isEqualTo(2);
    }

    @Test
    void 투표_가능_참여자가_모두_투표했으면_0을_반환한다() {
        Long a = participant(MEETING_ID, 1L, ParticipantState.COND_DONE);
        Long b = participant(MEETING_ID, 2L, ParticipantState.COND_DONE);

        vote(MEETING_ID, a);
        vote(MEETING_ID, b);

        assertThat(countParticipantsYetToVote()).isZero();
    }

    @Test
    void 여러_장소에_투표한_참여자도_한_명의_투표자로_취급한다() {
        Long a = participant(MEETING_ID, 1L, ParticipantState.COND_DONE);

        participant(MEETING_ID, 2L, ParticipantState.COND_DONE);
        vote(MEETING_ID, a, PLACE_ID);
        vote(MEETING_ID, a, PLACE_ID + 1);

        assertThat(countParticipantsYetToVote()).isEqualTo(1);
    }

    @Test
    void 투표하고_나간_참여자는_남은_미투표자_수에_영향을_주지_않는다() {
        Long a = participant(MEETING_ID, 1L, ParticipantState.COND_DONE);
        Long b = participant(MEETING_ID, 2L, ParticipantState.COND_DONE);
        Long c = participant(MEETING_ID, 3L, ParticipantState.COND_DONE);
        participant(MEETING_ID, 4L, ParticipantState.COND_DONE);

        vote(MEETING_ID, a);
        leave(a);
        vote(MEETING_ID, b);
        vote(MEETING_ID, c);

        // D는 미투표 상태이므로 확정 대상 아님
        assertThat(countParticipantsYetToVote()).isEqualTo(1);
    }

    @Test
    void 투표한_참여자가_나간_뒤_남은_인원이_모두_투표하면_0을_반환한다() {
        Long a = participant(MEETING_ID, 1L, ParticipantState.COND_DONE);
        Long b = participant(MEETING_ID, 2L, ParticipantState.COND_DONE);
        Long c = participant(MEETING_ID, 3L, ParticipantState.COND_DONE);
        Long d = participant(MEETING_ID, 4L, ParticipantState.COND_DONE);

        vote(MEETING_ID, a);
        vote(MEETING_ID, b);
        vote(MEETING_ID, c);
        leave(a);
        vote(MEETING_ID, d);

        assertThat(countParticipantsYetToVote()).isZero();
    }

    @Test
    void 투표하지_않고_나간_참여자는_세지_않는다() {
        Long a = participant(MEETING_ID, 1L, ParticipantState.COND_DONE);
        Long b = participant(MEETING_ID, 2L, ParticipantState.COND_DONE);

        vote(MEETING_ID, a);
        leave(b);

        assertThat(countParticipantsYetToVote()).isZero();
    }

    @Test
    void 나갔다가_다시_입장한_참여자는_이전_투표와_무관하게_신규_참여자로_제외된다() {
        Long a = participant(MEETING_ID, 1L, ParticipantState.COND_DONE);
        Long b = participant(MEETING_ID, 2L, ParticipantState.COND_DONE);

        vote(MEETING_ID, a);
        leave(a);
        participant(MEETING_ID, 1L, ParticipantState.NEW_RESTRICTED); // A 재입장
        vote(MEETING_ID, b);

        assertThat(countParticipantsYetToVote()).isZero();
    }

    private long countParticipantsYetToVote() {
        return participantRepository.countParticipantsYetToVote(MEETING_ID, ParticipantState.NEW_RESTRICTED);
    }

    private Long participant(Long meetingId, Long userId, ParticipantState state) {
        return em.persistAndFlush(Participant.create(meetingId, userId, state)).getId();
    }

    private void leave(Long participantId) {
        Participant participant = em.find(Participant.class, participantId);
        participant.leave();
        em.flush();
    }

    private void vote(Long meetingId, Long participantId) {
        vote(meetingId, participantId, PLACE_ID);
    }

    private void vote(Long meetingId, Long participantId, Long recommendedPlaceId) {
        em.persistAndFlush(PlaceVote.create(meetingId, participantId, recommendedPlaceId));
    }
}
