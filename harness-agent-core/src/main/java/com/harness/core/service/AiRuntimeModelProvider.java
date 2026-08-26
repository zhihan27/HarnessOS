package com.harness.core.service;

import com.harness.core.entity.ModelConfig;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class AiRuntimeModelProvider {
    private final ModelConfigService configService;
    private final AtomicReference<OpenAiStreamingChatModel> current = new AtomicReference<>();
    private volatile Long activeConfigId;

    public AiRuntimeModelProvider(ModelConfigService configService) {
        this.configService = configService;
        reload();
    }

    public synchronized void reload() {
        ModelConfig config = configService.activeOrFallback();
        OpenAiStreamingChatModel next = OpenAiStreamingChatModel.builder().apiKey(configService.decryptToken(config)).modelName(config.getModelName()).baseUrl(config.getBaseUrl()).build();
        current.set(next); activeConfigId = config.getId();
    }

    public OpenAiStreamingChatModel get() { return current.get(); }
    public Long getActiveConfigId() { return activeConfigId; }
}
