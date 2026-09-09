package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.domain.recommendation.dto.AreaRouteResult;
import com.moive.MoiveBE.domain.recommendation.dto.AreaScoreResult;
import com.moive.MoiveBE.domain.recommendation.dto.GoogleRouteMatrixResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendationRun;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaRecommendationService {

    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;

    private final AreaCenterService areaCenterService;
    private final AreaCandidateGenerationService areaCandidateGenerationService;
    private final AreaRouteService areaRouteService;
    private final RouteMatrixService routeMatrixService;
    private final AreaScoreService areaScoreService;
    private final AreaRecommendationSaveService areaRecommendationSaveService;

    private List<ParticipantPreference> getPreferences(
            Long meetingId
    ) {

        List<Participant> participants =
                participantRepository.findAllByMeetingIdAndLeftAtIsNull(
                        meetingId
                );

        List<Long> participantIds =
                participants.stream()
                        .map(Participant::getId)
                        .toList();

        return participantPreferenceRepository.findAllByParticipantIdIn(
                participantIds
        );
    }

    public RecommendationRun recommend(
            Long meetingId
    ) {

        List<ParticipantPreference> preferences =
                getPreferences(meetingId);

        AreaCenter center =
                areaCenterService.calculate(
                        preferences
                );

        List<AreaCandidate> candidates =
                areaCandidateGenerationService.generate(
                        center
                );

        List<GoogleRouteMatrixResponse> responses =
                areaRouteService.calculate(
                        preferences,
                        candidates
                );

        List<AreaRouteResult> routeResults =
                routeMatrixService.calculateAreaRouteResults(
                        candidates,
                        responses,
                        preferences.size()
                );

        List<AreaScoreResult> scoreResults =
                areaScoreService.calculateScores(
                        routeResults
                );

        List<AreaScoreResult> top3 =
                areaScoreService.selectTop3(
                        scoreResults
                );

        return areaRecommendationSaveService.save(
                meetingId,
                candidates,
                top3
        );
    }
}