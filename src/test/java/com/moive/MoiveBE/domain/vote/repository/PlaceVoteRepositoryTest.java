package com.moive.MoiveBE.domain.vote.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import org.junit.jupiter.api.BeforeEach;
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
class PlaceVoteRepositoryTest {

    @Autowired
    private PlaceVoteRepository placeVoteRepository;

    @Autowired
    private TestEntityManager em;

    private static final Long MEETING_ID = 1L;
    private static final Long OTHER_MEETING_ID = 99L;

    // 참여자 ID
    private static final Long A = 10L;
    private static final Long B = 20L;
    private static final Long C = 30L;

    private static final Long RECOMMENDATION_RUN_ID = 1000L;

    // 추천 장소
    private Long PLACE_1;
    private Long PLACE_2;
    private Long PLACE_3;

    @BeforeEach
    void setUpRecommendedPlaces() {
        Long areaId = area(RECOMMENDATION_RUN_ID, "합정역");
        PLACE_1 = place(areaId, "google-place-1", "카페", 3);
        PLACE_2 = place(areaId, "google-place-2", "레스토랑", 2);
        PLACE_3 = place(areaId, "google-place-3", "영화관", 1);
    }

    /**
     * existsByMeetingIdAndParticipantId
     */

    @Test
    void 투표한_적_있으면_true를_반환한다() {
        vote(MEETING_ID, A, PLACE_1);

        assertThat(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, A)).isTrue();
    }

    @Test
    void 투표한_적_없으면_false를_반환한다() {
        assertThat(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, A)).isFalse();
    }

    @Test
    void 같은_모임이어도_다른_참여자의_투표는_존재로_치지_않는다() {
        vote(MEETING_ID, A, PLACE_1);

        assertThat(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, B)).isFalse();
    }

    @Test
    void 같은_참여자여도_다른_모임의_투표는_존재로_치지_않는다() {
        vote(OTHER_MEETING_ID, A, PLACE_1);

        assertThat(placeVoteRepository.existsByMeetingIdAndParticipantId(MEETING_ID, A)).isFalse();
    }

    /**
     * countDistinctVoters
     */

    @Test
    void 한_참여자가_여러_장소를_다중_선택해도_투표자_수는_1명이다() {
        // 실존하는 추천 장소 3곳을 한 참여자가 모두 선택해도 투표자는 1명
        vote(MEETING_ID, A, PLACE_1);
        vote(MEETING_ID, A, PLACE_2);
        vote(MEETING_ID, A, PLACE_3);

        assertThat(placeVoteRepository.countDistinctVoters(MEETING_ID)).isEqualTo(1);
    }

    @Test
    void 서로_다른_참여자가_투표하면_각각_센다() {
        vote(MEETING_ID, A, PLACE_1);
        vote(MEETING_ID, B, PLACE_1);
        vote(MEETING_ID, B, PLACE_2);
        vote(MEETING_ID, C, PLACE_3);

        assertThat(placeVoteRepository.countDistinctVoters(MEETING_ID)).isEqualTo(3);
    }

    @Test
    void 투표가_없으면_0을_반환한다() {
        assertThat(placeVoteRepository.countDistinctVoters(MEETING_ID)).isZero();
    }

    @Test
    void 다른_모임의_투표는_집계에_섞이지_않는다() {
        vote(MEETING_ID, A, PLACE_1);
        vote(OTHER_MEETING_ID, B, PLACE_1);
        vote(OTHER_MEETING_ID, C, PLACE_2);

        assertThat(placeVoteRepository.countDistinctVoters(MEETING_ID)).isEqualTo(1);
    }

    /**
     * helpers
     */

    private Long area(Long recommendationRunId, String areaName) {
        return em.persistAndFlush(RecommendedArea.create(recommendationRunId, areaName)).getId();
    }

    private Long place(Long recommendedAreaId, String googlePlaceId, String category, int preferenceMatchCnt) {
        return em.persistAndFlush(
                RecommendedPlace.create(recommendedAreaId, googlePlaceId, category, preferenceMatchCnt)
        ).getId();
    }

    private void vote(Long meetingId, Long participantId, Long recommendedPlaceId) {
        em.persistAndFlush(PlaceVote.create(meetingId, participantId, recommendedPlaceId));
    }
}
