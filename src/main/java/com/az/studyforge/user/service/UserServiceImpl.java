package com.az.studyforge.user.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;

    @Override
    @Transactional
    public User upsertFromGoogle(GoogleProfile userInfo) {
         User user = userRepository.findByGoogleSub(userInfo.sub())
                 .orElseGet(() -> User.builder().googleSub(userInfo.sub()).build());

         user.setEmail(userInfo.email());
         user.setAvatarUrl(userInfo.avatarUrl());
         user.setFullName(userInfo.name());
         user.setLastLoginAt(Instant.now());
         return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getActiveUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.isLocked()) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        return user;
    }

    @Override
    @Transactional
    public User lockActiveUser(UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.isLocked()) throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        return user;
    }
}
