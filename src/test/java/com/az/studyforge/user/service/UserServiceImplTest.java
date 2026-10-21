package com.az.studyforge.user.service;

import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepository userRepository;

    private UserServiceImpl userService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepository);
        userId = UUID.randomUUID();
    }

    // G1
    @Test
    void getActiveUser_existingUnlockedUser_returnsUser() {
        User user = User.builder().id(userId).googleSub("sub-1").email("a@test.com").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(userService.getActiveUser(userId)).isSameAs(user);
    }

    // G2
    @Test
    void getActiveUser_notFound_throwsUserNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getActiveUser(userId))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // G3
    @Test
    void getActiveUser_lockedUser_throwsAccountLocked() {
        User locked = User.builder().id(userId).googleSub("sub-1").email("a@test.com").isLocked(true).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> userService.getActiveUser(userId))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }
}
