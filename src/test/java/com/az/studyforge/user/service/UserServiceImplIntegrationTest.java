package com.az.studyforge.user.service;

import com.az.studyforge.TestcontainersConfiguration;
import com.az.studyforge.user.dto.GoogleProfile;
import com.az.studyforge.user.entity.Role;
import com.az.studyforge.user.entity.User;
import com.az.studyforge.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class UserServiceImplIntegrationTest {

    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    private User existingUser(String sub, String email) {
        return userRepository.saveAndFlush(User.builder()
                .googleSub(sub)
                .email(email)
                .fullName("Old Name")
                .lastLoginAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());
    }

    private User reload(String sub) {
        entityManager.flush();
        entityManager.clear();
        return userRepository.findByGoogleSub(sub).orElseThrow();
    }

    private GoogleProfile profile(String sub, String email, String name) {
        return new GoogleProfile(sub, email, name, "https://img.test/" + sub + ".png");
    }

    @Test
    void upsert_newSub_createsUserWithDefaults() {
        userService.upsertFromGoogle(profile("s1", "a@test.com", "Toan"));

        User saved = reload("s1");
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("a@test.com");
        assertThat(saved.getFullName()).isEqualTo("Toan");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getDailyQuota()).isEqualTo(10);
        assertThat(saved.isLocked()).isFalse();
        assertThat(saved.getLastLoginAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void upsert_existingSub_updatesProfileWithoutCreatingNewUser() {
        User existing = existingUser("s1", "a@test.com");

        userService.upsertFromGoogle(profile("s1", "a@test.com", "New Name"));

        User updated = reload("s1");
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getFullName()).isEqualTo("New Name");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://img.test/s1.png");
    }

    @Test
    void upsert_existingSub_emailChanged_updatesEmail() {
        existingUser("s1", "a@test.com");

        userService.upsertFromGoogle(profile("s1", "b@test.com", "Toan"));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(reload("s1").getEmail()).isEqualTo("b@test.com");
    }

    @Test
    void upsert_emailTakenByOtherSub_bothUsersKeepEmail() {
        existingUser("s1", "a@test.com");

        userService.upsertFromGoogle(profile("s2", "a@test.com", "Other"));

        assertThat(reload("s2").getEmail()).isEqualTo("a@test.com");
        assertThat(reload("s1").getEmail()).isEqualTo("a@test.com");
        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void upsert_updatesLastLoginAt() {
        User existing = existingUser("s1", "a@test.com");
        Instant before = existing.getLastLoginAt();

        userService.upsertFromGoogle(profile("s1", "a@test.com", "Toan"));

        assertThat(reload("s1").getLastLoginAt()).isAfter(before);
    }

    @Test
    void upsert_existingLockedUser_keepsLockedFlag() {
        userRepository.saveAndFlush(User.builder()
                .googleSub("s1")
                .email("a@test.com")
                .isLocked(true)
                .build());

        userService.upsertFromGoogle(profile("s1", "a@test.com", "Toan"));

        assertThat(reload("s1").isLocked()).isTrue();
    }

    // U7
    @Test
    void save_nullEmail_rejectedByDatabase() {
        User invalid = User.builder().googleSub("s3").build();   // không có email

        assertThatThrownBy(() -> userRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
