package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.Participant;
import com.moive.MoiveBE.domain.meeting.entity.ParticipantPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipantPreferenceRepository extends JpaRepository<ParticipantPreference, Long> {
    Optional<ParticipantPreference> findByParticipantId(Long participantId);

    List<ParticipantPreference> findAllByParticipantIdIn(List<Long> participantIds);

    void deleteByParticipantId(Long participantId);
}
