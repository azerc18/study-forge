package com.az.studyforge.user.repository;

import com.az.studyforge.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByGoogleSub(String sub);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Admin: tìm theo email / tên, mới nhất trước. {@code pattern} đã ở dạng "%abc%" (chữ thường);
     * không tìm thì truyền "%" (khớp tất cả) — tránh tham số null trong JPQL với Postgres.
     */
    @Query(value = """
            select u from User u
            where lower(u.email) like :pattern or lower(coalesce(u.fullName, '')) like :pattern
            order by u.createdAt desc
            """,
            countQuery = """
            select count(u) from User u
            where lower(u.email) like :pattern or lower(coalesce(u.fullName, '')) like :pattern
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);
}
