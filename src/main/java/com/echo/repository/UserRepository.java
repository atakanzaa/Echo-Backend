package com.echo.repository;

import com.echo.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Son N günde aktif olan kullanıcılar — haftalık memory update scheduler için */
    @Query("SELECT u FROM User u WHERE u.lastEntryDate >= :since")
    List<User> findUsersWithRecentEntries(@Param("since") LocalDate since);

    /** Soft-deleted accounts that have outlived the retention window and are due for hard-delete. */
    @Query("SELECT u FROM User u WHERE u.accountDeletionRequestedAt IS NOT NULL " +
            "AND u.accountDeletionRequestedAt < :before")
    List<User> findUsersDueForHardDelete(@Param("before") OffsetDateTime before);
}
