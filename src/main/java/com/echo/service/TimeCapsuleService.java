package com.echo.service;

import com.echo.domain.capsule.TimeCapsule;
import com.echo.domain.subscription.FeatureKey;
import com.echo.domain.user.User;
import com.echo.dto.request.CreateCapsuleRequest;
import com.echo.dto.response.PagedResponse;
import com.echo.dto.response.TimeCapsuleResponse;
import com.echo.exception.CapsuleStillLockedException;
import com.echo.exception.QuotaExceededException;
import com.echo.exception.ResourceNotFoundException;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.TimeCapsuleRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimeCapsuleService {

    private final TimeCapsuleRepository timeCapsuleRepository;
    private final UserRepository        userRepository;
    private final EntitlementService    entitlementService;

    @Transactional(readOnly = true)
    public PagedResponse<TimeCapsuleResponse> getCapsules(UUID userId, Pageable pageable) {
        return PagedResponse.from(
                timeCapsuleRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                TimeCapsuleResponse::from
        );
    }

    @Transactional
    public TimeCapsuleResponse createCapsule(UUID userId, CreateCapsuleRequest request) {
        int limit = entitlementService.getLimit(userId, FeatureKey.ACTIVE_TIME_CAPSULES);
        int activeCapsules = timeCapsuleRepository.countByUserIdAndStatus(userId, "sealed");
        if (limit != -1 && activeCapsules >= limit) {
            throw new QuotaExceededException(
                    "CAPSULE_LIMIT",
                    "Active time capsule limit reached. Upgrade to Premium for more capsules."
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        TimeCapsule capsule = TimeCapsule.builder()
                .user(user)
                .title(request.title())
                .contentText(request.contentText())
                .contentType(request.contentType())
                .sealedAt(OffsetDateTime.now())
                .unlockAt(OffsetDateTime.parse(request.unlockAt()))
                .status("sealed")
                .build();
        return TimeCapsuleResponse.from(timeCapsuleRepository.save(capsule));
    }

    @Transactional
    public TimeCapsuleResponse getCapsule(UUID capsuleId, UUID userId) {
        TimeCapsule capsule = timeCapsuleRepository.findByIdAndUserId(capsuleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Capsule not found"));
        if (!capsule.isUnlocked()) {
            throw new CapsuleStillLockedException(
                    "This capsule is locked until " + capsule.getUnlockAt());
        }
        if (!"opened".equals(capsule.getStatus())) {
            capsule.setStatus("opened");
            capsule.setOpenedAt(OffsetDateTime.now());
            timeCapsuleRepository.save(capsule);
        }
        return TimeCapsuleResponse.from(capsule);
    }

    @Transactional
    public void deleteCapsule(UUID capsuleId, UUID userId) {
        TimeCapsule capsule = timeCapsuleRepository.findByIdAndUserId(capsuleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Capsule not found"));
        if (!"sealed".equals(capsule.getStatus())) {
            throw new IllegalArgumentException("Only sealed capsules can be deleted");
        }
        timeCapsuleRepository.delete(capsule);
    }
}
