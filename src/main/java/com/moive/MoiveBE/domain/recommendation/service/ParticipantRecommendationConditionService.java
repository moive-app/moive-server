package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Activity;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.entity.PreferenceActivity;
import com.moive.MoiveBE.domain.meeting.repository.ActivityRepository;
import com.moive.MoiveBE.domain.meeting.repository.PreferenceActivityRepository;
import com.moive.MoiveBE.domain.recommendation.dto.ParticipantRecommendationCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ParticipantRecommendationConditionService {

    private final PreferenceActivityRepository preferenceActivityRepository;
    private final ActivityRepository activityRepository;

    public List<ParticipantRecommendationCondition> createConditions(
            List<ParticipantPreference> preferences
    ) {
        List<Long> preferenceIds = preferences.stream()
                .map(ParticipantPreference::getId)
                .toList();

        List<PreferenceActivity> preferenceActivities =
                preferenceActivityRepository.findAllByPreferenceIdIn(
                        preferenceIds
                );

        List<Long> activityIds = preferenceActivities.stream()
                .map(PreferenceActivity::getActivityId)
                .distinct()
                .toList();

        Map<Long, Activity> activityMap =
                activityRepository.findAllById(activityIds).stream()
                        .collect(Collectors.toMap(
                                Activity::getId,
                                Function.identity()
                        ));

        Map<Long, Set<String>> preferenceTypeMap =
                preferenceActivities.stream()
                        .filter(preferenceActivity ->
                                activityMap.containsKey(
                                        preferenceActivity.getActivityId()
                                )
                        )
                        .collect(Collectors.groupingBy(
                                PreferenceActivity::getPreferenceId,
                                Collectors.mapping(
                                        preferenceActivity ->
                                                activityMap.get(
                                                        preferenceActivity.getActivityId()
                                                ).getName().getLabel(),
                                        Collectors.toSet()
                                )
                        ));

        return IntStream.range(0, preferences.size())
                .mapToObj(originIndex -> {
                    ParticipantPreference preference =
                            preferences.get(originIndex);

                    Set<String> preferenceTypes =
                            preferenceTypeMap.getOrDefault(
                                    preference.getId(),
                                    Set.of()
                            );

                    return new ParticipantRecommendationCondition(
                            originIndex,
                            preferenceTypes,
                            preference.getMaxTravelMinutes()
                    );
                })
                .toList();
    }
}