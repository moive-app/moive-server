package com.moive.MoiveBE.domain.notification.repository;

import com.moive.MoiveBE.domain.notification.entity.Notification;
import com.moive.MoiveBE.domain.notification.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.createdAt >= :from AND (:cursor IS NULL OR n.id < :cursor) ORDER BY n.id DESC LIMIT :size")
    List<Notification> findByUserIdWithCursor(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("cursor") Long cursor,
            @Param("size") int size
    );

    boolean existsByUserIdAndIsReadFalseAndCreatedAtAfter(Long userId, LocalDateTime from);

    List<Notification> findByUserIdAndMeetingIdAndTypeAndIsReadFalse(Long userId, Long meetingId, NotificationType type);

    void deleteAllByUserId(Long userId);
}
