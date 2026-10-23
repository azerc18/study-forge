package com.az.studyforge.admin.service;

import com.az.studyforge.admin.dto.AdminUserRow;
import com.az.studyforge.admin.dto.UpdateUserRequest;
import com.az.studyforge.auth.repository.RefreshTokenRepository;
import com.az.studyforge.common.dto.PageResponse;
import com.az.studyforge.common.exception.AppException;
import com.az.studyforge.common.exception.ErrorCode;
import com.az.studyforge.generation.config.GenerationProperties;
import com.az.studyforge.generation.dto.UserQuizSetCount;
import com.az.studyforge.generation.repository.QuizSetRepository;
import com.az.studyforge.user.entity.Role;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final QuizSetRepository quizSetRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GenerationProperties props;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserRow> list(String q, int page, int size) {
        String pattern = q == null || q.isBlank() ? "%" : "%" + q.strip().toLowerCase(Locale.ROOT) + "%";
        Page<User> users = userRepository.search(pattern,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));

        Map<UUID, UserQuizSetCount> counts = counts(users.getContent().stream().map(User::getId).toList());
        return PageResponse.from(users.map(u -> toRow(u, counts.get(u.getId()))));
    }

    /**
     * Sửa quota / khoá. Không cho tự khoá mình và không khoá admin khác.
     * Khoá → thu hồi mọi refresh token: access token đang có còn sống tối đa 15 phút,
     * nhưng các API tốn tài nguyên đều kiểm tra isLocked.
     */
    @Transactional
    public AdminUserRow update(UUID adminId, UUID targetId, UpdateUserRequest req) {
        User user = userRepository.findById(targetId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (Boolean.TRUE.equals(req.locked()) && (user.getId().equals(adminId) || user.getRole() == Role.ADMIN)) {
            throw new AppException(ErrorCode.CANNOT_MODIFY_SELF);
        }

        if (req.dailyQuota() != null) user.setDailyQuota(req.dailyQuota());

        if (req.locked() != null && req.locked() != user.isLocked()) {
            user.setLocked(req.locked());
            if (req.locked()) {
                int revoked = refreshTokenRepository.revokeAllByUserId(user.getId(), clock.instant());
                log.info("Admin {} locked user {} ({} refresh token(s) revoked)", adminId, user.getId(), revoked);
            } else {
                log.info("Admin {} unlocked user {}", adminId, user.getId());
            }
        }

        return toRow(user, counts(List.of(user.getId())).get(user.getId()));
    }

    private Map<UUID, UserQuizSetCount> counts(List<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return quizSetRepository.countByUsers(userIds, startOfToday()).stream()
                .collect(Collectors.toMap(UserQuizSetCount::userId, Function.identity()));
    }

    private Instant startOfToday() {
        ZoneId zone = props.quotaZone();
        return LocalDate.now(clock.withZone(zone)).atStartOfDay(zone).toInstant();
    }

    private static AdminUserRow toRow(User u, UserQuizSetCount c) {
        return new AdminUserRow(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getDailyQuota(),
                u.isLocked(), u.getCreatedAt(), u.getLastLoginAt(),
                c == null ? 0 : c.since(), c == null ? 0 : c.total());
    }
}
