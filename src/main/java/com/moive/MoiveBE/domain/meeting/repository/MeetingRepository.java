package com.moive.MoiveBE.domain.meeting.repository;

import com.moive.MoiveBE.domain.meeting.entity.Meeting;
import com.moive.MoiveBE.domain.meeting.entity.MeetingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    Optional<Meeting> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    List<Meeting> findAllByStatus(MeetingStatus status);

    List<Meeting> findAllByStatusAndScheduledDateBefore(MeetingStatus status, LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.id = :id")
    Optional<Meeting> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Meeting m where m.inviteCode = :inviteCode")
    Optional<Meeting> findByInviteCodeForUpdate(@Param("inviteCode") String inviteCode);

    // 모임 일정이 지난 모임의 상태 일괄 변경 (CONFIRMED -> COMPLETED)
    @Modifying
    @Query("""
            update Meeting m
            set m.status = :toStatus,
                m.updatedAt = :updatedAt
            where m.status = :fromStatus
              and m.scheduledDate < :date
            """)
    int updateStatusByStatusAndScheduledDateBefore(
            @Param("fromStatus") MeetingStatus fromStatus,
            @Param("toStatus") MeetingStatus toStatus,
            @Param("date") LocalDate date,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}