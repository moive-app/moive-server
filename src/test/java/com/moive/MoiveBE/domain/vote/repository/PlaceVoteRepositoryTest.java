package com.moive.MoiveBE.domain.vote.repository;

import com.moive.MoiveBE.domain.recommendation.entity.RecommendedArea;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.vote.dto.PlaceVoteSummary;
import com.moive.MoiveBE.domain.vote.entity.PlaceVote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
     * aggregateByPlace
     */

    @Test
    void 장소별_득표수와_나의_투표_여부를_함께_집계한다() {
        vote(MEETING_ID, A, PLACE_1);
        vote(MEETING_ID, A, PLACE_2);
        vote(MEETING_ID, B, PLACE_1);
        vote(MEETING_ID, C, PLACE_1);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        assertThat(result).extracting(PlaceVoteSummary::recommendedPlaceId, PlaceVoteSummary::voterCnt, PlaceVoteSummary::isVotedByMe)
                .containsExactlyInAnyOrder(
                        tuple(PLACE_1, 3L, true),
                        tuple(PLACE_2, 1L, true)
                );
    }

    @Test
    void 유저가_투표하지_않은_장소는_votedByMe가_false다() {
        vote(MEETING_ID, A, PLACE_1);
        vote(MEETING_ID, B, PLACE_2);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        PlaceVoteSummary place2Summary = result.stream()
                .filter(s -> s.recommendedPlaceId().equals(PLACE_2))
                .findFirst()
                .orElseThrow();
        assertThat(place2Summary.isVotedByMe()).isFalse();
    }

    @Test
    void 투표_내역이_없는_장소는_집계_결과에_나타나지_않는다() {
        vote(MEETING_ID, A, PLACE_1);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        assertThat(result).extracting(PlaceVoteSummary::recommendedPlaceId).containsExactly(PLACE_1);
    }

    @Test
    void 투표가_하나도_없으면_빈_리스트를_반환한다() {
        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        assertThat(result).isEmpty();
    }

    @Test
    void 다른_모임의_투표는_집계에_섞이지_않는다_aggregateByPlace() {
        vote(MEETING_ID, A, PLACE_1);
        vote(OTHER_MEETING_ID, B, PLACE_1);
        vote(OTHER_MEETING_ID, C, PLACE_2);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        assertThat(result).extracting(PlaceVoteSummary::recommendedPlaceId, PlaceVoteSummary::voterCnt)
                .containsExactly(tuple(PLACE_1, 1L));
    }

    @Test
    void googlePlaceId가_같은_추천_장소는_하나로_병합해_득표수를_합산한다() {
        // PLACE_1과 같은 실제 장소(google-place-1)를 다른 추천 지역에서 또 추천한 경우
        Long duplicatedPlace1 = duplicatedPlace("google-place-1");
        vote(MEETING_ID, A, PLACE_1);
        vote(MEETING_ID, B, duplicatedPlace1);
        vote(MEETING_ID, C, PLACE_2);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        assertThat(result).extracting(PlaceVoteSummary::recommendedPlaceId, PlaceVoteSummary::voterCnt)
                .containsExactlyInAnyOrder(
                        tuple(PLACE_1, 2L),
                        tuple(PLACE_2, 1L)
                );
    }

    @Test
    void 한_참여자가_병합되는_두_추천_장소에_모두_투표해도_1표로_센다() {
        Long duplicatedPlace1 = duplicatedPlace("google-place-1");
        vote(MEETING_ID, A, PLACE_1);
        vote(MEETING_ID, A, duplicatedPlace1);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        assertThat(result).extracting(PlaceVoteSummary::recommendedPlaceId, PlaceVoteSummary::voterCnt)
                .containsExactly(tuple(PLACE_1, 1L));
    }

    @Test
    void 병합된_장소는_대표가_아닌_추천_장소에_투표했어도_votedByMe가_true다() {
        Long duplicatedPlace1 = duplicatedPlace("google-place-1");
        vote(MEETING_ID, A, PLACE_1);
        vote(MEETING_ID, B, duplicatedPlace1);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, B);

        assertThat(result).extracting(PlaceVoteSummary::recommendedPlaceId, PlaceVoteSummary::isVotedByMe)
                .containsExactly(tuple(PLACE_1, true));
    }

    @Test
    void 병합된_장소의_대표_id는_투표된_추천_장소_중_가장_작은_id다() {
        Long duplicatedPlace1 = duplicatedPlace("google-place-1");
        // PLACE_1(더 작은 id)에는 투표가 없고 duplicatedPlace1에만 투표가 있는 경우
        vote(MEETING_ID, A, duplicatedPlace1);

        List<PlaceVoteSummary> result = placeVoteRepository.aggregateByPlace(MEETING_ID, A);

        assertThat(result).extracting(PlaceVoteSummary::recommendedPlaceId).containsExactly(duplicatedPlace1);
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

    // 다른 추천 지역에서 같은 장소가 추천되는 경우 (recommendedPlaceId는 다르고 googlePlaceId는 동일)
    private Long duplicatedPlace(String googlePlaceId) {
        Long otherAreaId = area(RECOMMENDATION_RUN_ID, "강남역");
        return place(otherAreaId, googlePlaceId, "카페", 5);
    }

    private void vote(Long meetingId, Long participantId, Long recommendedPlaceId) {
        em.persistAndFlush(PlaceVote.create(meetingId, participantId, recommendedPlaceId));
    }
}
