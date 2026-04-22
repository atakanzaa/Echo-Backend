package com.echo.service;

import com.echo.domain.user.User;
import com.echo.dto.request.UpdateProfileRequest;
import com.echo.dto.response.UserResponse;
import com.echo.exception.ResourceNotFoundException;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.timezone() != null) {
            user.setTimezone(request.timezone());
        }
        if (request.language() != null) {
            user.setPreferredLanguage(request.language());
        }
        return UserResponse.from(userRepository.save(user));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
