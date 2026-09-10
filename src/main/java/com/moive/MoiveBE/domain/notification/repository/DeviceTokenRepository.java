package com.moive.MoiveBE.domain.notification.repository;

import com.moive.MoiveBE.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByDeviceId(String deviceId);

    List<DeviceToken> findAllByUserId(Long userId);

    Optional<DeviceToken> findByDeviceIdAndUserId(String deviceId, Long userId);
}
