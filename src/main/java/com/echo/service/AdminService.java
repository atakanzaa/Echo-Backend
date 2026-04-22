package com.echo.service;

import com.echo.ai.AIProviderRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AIProviderRouter router;

    public String getActiveProvider() {
        return router.activeProvider();
    }

    public String getActiveTranscriptionProvider() {
        return router.activeTranscriptionProvider();
    }

    public void switchProvider(String provider) {
        router.switchProvider(provider);
    }
}
