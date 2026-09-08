package com.moive.MoiveBE.domain.user.service;

import com.moive.MoiveBE.domain.user.dto.MyInfoResponse;
import com.moive.MoiveBE.domain.user.entity.User;
import com.moive.MoiveBE.domain.user.repository.UserRepository;
import com.moive.MoiveBE.global.exception.CustomErrorCode;
import com.moive.MoiveBE.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public MyInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new CustomException(CustomErrorCode.USER_NOT_FOUND)
                );

        return new MyInfoResponse(
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getEmail()
        );
    }
}