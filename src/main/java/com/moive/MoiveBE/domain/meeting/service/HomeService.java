package com.moive.MoiveBE.domain.meeting.service;

import com.moive.MoiveBE.domain.meeting.dto.HomeResponse;
import com.moive.MoiveBE.domain.meeting.dto.MeetingItemDto;
import com.moive.MoiveBE.domain.meeting.dto.MeetingListResponse;
import com.moive.MoiveBE.domain.meeting.entity.*;
import com.moive.MoiveBE.domain.meeting.repository.*;
import com.moive.MoiveBE.domain.recommendation.client.GooglePlacesClient;
import com.moive.MoiveBE.domain.recommendation.dto.GooglePlaceDetailsResponse;
import com.moive.MoiveBE.domain.recommendation.entity.RecommendedPlace;
import com.moive.MoiveBE.domain.recommendation.repository.RecommendedPlaceRepository;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private final MeetingRepository meetingRepository;
    private final MeetingPurposeRepository meetingPurposeRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RecommendedPlaceRepository recommendedPlaceRepository;
    private final GooglePlacesClient googlePlacesClient;

    public HomeResponse getHome(String filterStr) {
        Long currentUserId = getCurrentUserId();
        MeetingFilter filter = MeetingFilter.from(filterStr);

        List<Participant> myParticipations = participantRepository
                .findAllByUserIdAndLeftAtIsNullOrderByIdAsc(currentUserId);

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        String nickname = currentUser.getNickname();

        if (myParticipations.isEmpty()) {
            return new HomeResponse(nickname, List.of(), List.of());
        }

        List<Long> myMeetingIds = myParticipations.stream().map(Participant::getMeetingId).toList();

        Map<Long, Meeting> meetingMap = meetingRepository.findAllById(myMeetingIds)
                .stream().collect(Collectors.toMap(Meeting::getId, Function.identity()));

        Map<Long, MeetingPurpose> purposeMap = meetingPurposeRepository.findAllByMeetingIdIn(myMeetingIds)
                .stream().collect(Collectors.toMap(MeetingPurpose::getMeetingId, Function.identity()));

        // 참여자 프로필 이미지 배치 조회
        List<Participant> allHomeParticipants = participantRepository
                .findAllByMeetingIdInAndLeftAtIsNullOrderByJoinedAtAsc(myMeetingIds);
        Map<Long, List<Participant>> homeParticipantsByMeeting = allHomeParticipants.stream()
                .collect(Collectors.groupingBy(Participant::getMeetingId));
        List<Long> homeUserIds = allHomeParticipants.stream().map(Participant::getUserId).distinct().toList();
        Map<Long, User> homeUserMap = userRepository.findAllById(homeUserIds)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        // myMeetings (filter 적용, 가입 순 유지)
        List<MeetingItemDto> myMeetings = myParticipations.stream()
                .map(p -> meetingMap.get(p.getMeetingId()))
                .filter(Objects::nonNull)
                .filter(m -> filter.matches(m.getStatus()))
                .map(m -> toMeetingItem(m, purposeMap, homeParticipantsByMeeting, homeUserMap))
                .toList();

        // confirmedMeetings (CONFIRMED, scheduledDate 있는 것만, dDay 오름차순)
        List<Meeting> confirmed = myParticipations.stream()
                .map(p -> meetingMap.get(p.getMeetingId()))
                .filter(Objects::nonNull)
                .filter(m -> m.getStatus() == MeetingStatus.CONFIRMED && m.getScheduledDate() != null)
                .sorted(Comparator.comparingLong(m -> ChronoUnit.DAYS.between(LocalDate.now(), m.getScheduledDate())))
                .toList();

        List<HomeResponse.ConfirmedMeetingDto> confirmedDtos = buildConfirmedDtos(confirmed);

        return new HomeResponse(nickname, confirmedDtos, myMeetings);
    }

    public MeetingListResponse getMeetings(String filterStr, Long cursor, int size) {
        Long currentUserId = getCurrentUserId();
        MeetingFilter filter = MeetingFilter.from(filterStr);

        List<Participant> allParticipations = participantRepository
                .findAllByUserIdAndLeftAtIsNullOrderByIdAsc(currentUserId);

        // cursor 이후 항목만 추출
        List<Participant> afterCursor = cursor == null ? allParticipations
                : allParticipations.stream().filter(p -> p.getId() > cursor).toList();

        List<Long> meetingIds = afterCursor.stream().map(Participant::getMeetingId).toList();

        Map<Long, Meeting> meetingMap = meetingRepository.findAllById(meetingIds)
                .stream().collect(Collectors.toMap(Meeting::getId, Function.identity()));

        // 필터 적용
        List<Participant> filtered = afterCursor.stream()
                .filter(p -> {
                    Meeting m = meetingMap.get(p.getMeetingId());
                    return m != null && filter.matches(m.getStatus());
                })
                .toList();

        boolean hasNext = filtered.size() > size;
        List<Participant> page = filtered.subList(0, Math.min(size, filtered.size()));

        List<Long> pageMeetingIds = page.stream().map(Participant::getMeetingId).toList();
        Map<Long, MeetingPurpose> purposeMap = meetingPurposeRepository.findAllByMeetingIdIn(pageMeetingIds)
                .stream().collect(Collectors.toMap(MeetingPurpose::getMeetingId, Function.identity()));

        // 참여자 프로필 이미지 배치 조회
        List<Participant> allParticipants = participantRepository
                .findAllByMeetingIdInAndLeftAtIsNullOrderByJoinedAtAsc(pageMeetingIds);
        Map<Long, List<Participant>> participantsByMeeting = allParticipants.stream()
                .collect(Collectors.groupingBy(Participant::getMeetingId));
        List<Long> allUserIds = allParticipants.stream().map(Participant::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(allUserIds)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<MeetingItemDto> meetings = page.stream()
                .map(p -> toMeetingItem(meetingMap.get(p.getMeetingId()), purposeMap, participantsByMeeting, userMap))
                .filter(Objects::nonNull)
                .toList();

        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        return new MeetingListResponse(meetings, hasNext, nextCursor);
    }

    private List<HomeResponse.ConfirmedMeetingDto> buildConfirmedDtos(List<Meeting> confirmed) {
        if (confirmed.isEmpty()) return List.of();

        List<Long> confirmedIds = confirmed.stream().map(Meeting::getId).toList();

        // 참여자 프로필 이미지 (모임별 최대 3명)
        List<Participant> allParticipants = participantRepository
                .findAllByMeetingIdInAndLeftAtIsNullOrderByJoinedAtAsc(confirmedIds);

        Map<Long, List<Participant>> participantsByMeeting = allParticipants.stream()
                .collect(Collectors.groupingBy(Participant::getMeetingId));

        List<Long> allUserIds = allParticipants.stream().map(Participant::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(allUserIds)
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return confirmed.stream()
                .map(m -> {
                    List<String> profileImages = participantsByMeeting
                            .getOrDefault(m.getId(), List.of()).stream()
                            .limit(3)
                            .map(p -> {
                                User u = userMap.get(p.getUserId());
                                return u != null ? u.getProfileImageUrl() : null;
                            })
                            .toList();

                    int dDay = (int) ChronoUnit.DAYS.between(LocalDate.now(), m.getScheduledDate());

                    return new HomeResponse.ConfirmedMeetingDto(
                            m.getId(),
                            m.getName(),
                            resolveConfirmedPlaceName(m.getConfirmedPlaceId()),
                            m.getScheduledDate().toString(),
                            m.getScheduledTime() != null ? m.getScheduledTime().toString() : null,
                            profileImages,
                            m.getParticipantCnt(),
                            dDay
                    );
                })
                .toList();
    }

    private String resolveConfirmedPlaceName(Long confirmedPlaceId) {
        if (confirmedPlaceId == null) return null;
        return recommendedPlaceRepository.findById(confirmedPlaceId)
                .map(RecommendedPlace::getGooglePlaceId)
                .map(googlePlaceId -> {
                    GooglePlaceDetailsResponse details = googlePlacesClient.getPlaceSummaryDetails(googlePlaceId);
                    return details != null && details.displayName() != null ? details.displayName().text() : null;
                })
                .orElse(null);
    }

    private MeetingItemDto toMeetingItem(
            Meeting m,
            Map<Long, MeetingPurpose> purposeMap,
            Map<Long, List<Participant>> participantsByMeeting,
            Map<Long, User> userMap
    ) {
        if (m == null) return null;
        MeetingPurpose purpose = purposeMap.get(m.getId());
        List<Participant> meetingParticipants = participantsByMeeting
                .getOrDefault(m.getId(), List.of()).stream()
                .limit(3)
                .toList();
        List<String> participantImages = meetingParticipants.stream()
                .map(p -> {
                    User u = userMap.get(p.getUserId());
                    return u != null ? u.getProfileImageUrl() : null;
                })
                .toList();
        return new MeetingItemDto(
                m.getId(),
                m.getName(),
                purpose != null ? purpose.getPurposeType().name() : null,
                m.getStatus().name(),
                m.getStatus().getLabel(),
                m.getScheduledDate() != null ? m.getScheduledDate().toString() : null,
                m.getScheduledTime() != null ? m.getScheduledTime().toString() : null,
                m.getParticipantCnt(),
                participantImages
        );
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private enum MeetingFilter {
        ALL, UPCOMING, PAST;

        static MeetingFilter from(String value) {
            if (value == null) return ALL;
            return switch (value.toUpperCase()) {
                case "UPCOMING" -> UPCOMING;
                case "PAST" -> PAST;
                default -> ALL;
            };
        }

        boolean matches(MeetingStatus status) {
            return switch (this) {
                case ALL -> true;
                case UPCOMING -> status == MeetingStatus.CONDITION_INPUT
                        || status == MeetingStatus.VOTING
                        || status == MeetingStatus.CONFIRMED;
                case PAST -> status == MeetingStatus.COMPLETED;
            };
        }
    }
}
