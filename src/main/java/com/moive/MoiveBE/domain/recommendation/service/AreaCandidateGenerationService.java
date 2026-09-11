package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.recommendation.client.OpenAIClient;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidate;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCandidateResponse;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaCandidateGenerationService {

    private static final int MIN_CANDIDATE_COUNT = 3;

    private final OpenAIClient openAIClient;
    private final AreaCandidateService areaCandidateService;

    public List<AreaCandidate> generate(AreaCenter center) {

        AreaCandidateResponse firstResponse =
                openAIClient.generateAreaCandidates(center);

        List<AreaCandidate> candidates =
                new ArrayList<>(
                        areaCandidateService.resolveCandidates(
                                firstResponse,
                                center
                        )
                );

        if (candidates.size() >= MIN_CANDIDATE_COUNT) {
            return candidates;
        }

        AreaCandidateResponse retryResponse =
                openAIClient.generateAreaCandidates(
                        center,
                        firstResponse.areas()
                );

        List<AreaCandidate> retryCandidates =
                areaCandidateService.resolveCandidates(
                        retryResponse,
                        center
                );

        candidates.addAll(retryCandidates);

        if (candidates.size() < MIN_CANDIDATE_COUNT) {
            throw new CustomException(
                    CustomErrorCode.INSUFFICIENT_AREA_CANDIDATES
            );
        }

        return candidates;
    }
}