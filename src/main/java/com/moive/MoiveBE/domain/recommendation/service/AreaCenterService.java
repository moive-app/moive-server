package com.moive.MoiveBE.domain.recommendation.service;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantPreferenceRepository;
import com.moive.MoiveBE.domain.meeting.repository.ParticipantRepository;
import com.moive.MoiveBE.domain.recommendation.dto.AreaCenter;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AreaCenterService {

    private final ParticipantRepository participantRepository;
    private final ParticipantPreferenceRepository participantPreferenceRepository;

    public AreaCenter calculate(Long meetingId) {

        List<Participant> participants =
                participantRepository.findAllByMeetingIdAndLeftAtIsNull(meetingId);

        List<Long> participantIds = participants.stream()
                .map(Participant::getId)
                .toList();

        List<ParticipantPreference> preferences =
                participantPreferenceRepository.findAllByParticipantIdIn(participantIds);

        if (preferences.isEmpty()) {
            throw new CustomException(
                    CustomErrorCode.RECOMMENDATION_SOURCE_LOCATION_NOT_FOUND
            );
        }

        double averageLatitude = preferences.stream()
                .mapToDouble(preference ->
                        preference.getDepartureLatitude().doubleValue())
                .average()
                .orElseThrow();

        double averageLongitude = preferences.stream()
                .mapToDouble(preference ->
                        preference.getDepartureLongitude().doubleValue())
                .average()
                .orElseThrow();

        return new AreaCenter(
                averageLatitude,
                averageLongitude
        );
    }
}