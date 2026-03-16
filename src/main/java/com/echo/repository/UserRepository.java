package com.echo.repository;

import com.echo.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Son N günde aktif olan kullanıcılar — haftalık memory update scheduler için */
    @Query("SELECT u FROM User u WHERE u.lastEntryDate >= :since")
    List<User> findUsersWithRecentEntries(@Param("since") LocalDate since);
}
