package com.echo.service;

import com.echo.domain.capsule.TimeCapsule;
import com.echo.domain.user.User;
import com.echo.dto.request.CreateCapsuleRequest;
import com.echo.dto.response.TimeCapsuleResponse;
import com.echo.exception.CapsuleStillLockedException;
import com.echo.exception.ResourceNotFoundException;
import com.echo.exception.UnauthorizedException;
import com.echo.repository.TimeCapsuleRepository;
import com.echo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TimeCapsuleService {

    private final TimeCapsuleRepository timeCapsuleRepository;
    private final UserRepository        userRepository;

    @Transactional(readOnly = true)
    public List<TimeCapsuleResponse> getCapsules(UUID userId) {
        return timeCapsuleRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(TimeCapsuleResponse::from).toList();
    }

    @Transactional
    public TimeCapsuleResponse createCapsule(UUID userId, CreateCapsuleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı"));
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
                .orElseThrow(() -> new ResourceNotFoundException("Kapsül bulunamadı"));
        if (!capsule.isUnlocked()) {
            throw new CapsuleStillLockedException(
                    "Bu kapsül " + capsule.getUnlockAt() + " tarihine kadar kilitli");
        }
        if ("sealed".equals(capsule.getStatus())) {
            capsule.setStatus("opened");
            capsule.setOpenedAt(OffsetDateTime.now());
            timeCapsuleRepository.save(capsule);
        }
        return TimeCapsuleResponse.from(capsule);
    }

    @Transactional
    public void deleteCapsule(UUID capsuleId, UUID userId) {
        TimeCapsule capsule = timeCapsuleRepository.findByIdAndUserId(capsuleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Kapsül bulunamadı"));
        if (!"sealed".equals(capsule.getStatus())) {
            throw new IllegalArgumentException("Sadece kilitli kapsüller silinebilir");
        }
        timeCapsuleRepository.delete(capsule);
    }
}
