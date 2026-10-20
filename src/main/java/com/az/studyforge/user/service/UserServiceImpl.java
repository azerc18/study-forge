package com.az.studyforge.user.service;

import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
}
