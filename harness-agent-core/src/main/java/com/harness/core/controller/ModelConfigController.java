package com.harness.core.controller;

import com.harness.core.entity.ModelConfig;
import com.harness.core.service.AiRuntimeModelProvider;
import com.harness.core.service.AiServiceFactory;
import com.harness.core.service.ModelConfigService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 提供聊天模型和向量模型配置的查询、维护及激活接口。
 */
@RestController
@RequestMapping("/api/model-configs")
@RequiredArgsConstructor
public class ModelConfigController {
    private final ModelConfigService service;
    private final AiRuntimeModelProvider provider;
    private final AiServiceFactory factory;

    @GetMapping
    public List<Response> list() {
        return service.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/active")
    public Response active() {
        return toResponse(service.activeOrFallback());
    }

    /** 查询当前启用的向量模型配置。 */
    @GetMapping("/active/embedding")
    public Response activeEmbedding() {
        ModelConfig config = service.activeEmbeddingOrNull();
        return config == null ? null : toResponse(config);
    }

    @PostMapping
    public Response create(@RequestBody Request req) {
        ModelConfig c = service.create(
                req.name(),
                req.provider(),
                req.modelType(),
                req.baseUrl(),
                req.modelName(),
                req.token(),
                req.activate()
        );
        refreshIfActive(c);
        return toResponse(c);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @RequestBody Request req) {
        ModelConfig c = service.update(
                id,
                req.name(),
                req.provider(),
                req.modelType(),
                req.baseUrl(),
                req.modelName(),
                req.token()
        );
        if (Boolean.TRUE.equals(c.getActive())) refresh();
        return toResponse(c);
    }

    @PostMapping("/{id}/activate")
    public Response activate(@PathVariable Long id) {
        ModelConfig c = service.activate(id);
        refresh();
        return toResponse(c);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> handleValidation(RuntimeException e) {
        return java.util.Map.of("error", e.getMessage());
    }

    private void refreshIfActive(ModelConfig c) {
        if (Boolean.TRUE.equals(c.getActive())) refresh();
    }

    private void refresh() {
        provider.reload();
        factory.reload();
    }

    private Response toResponse(ModelConfig c) {
        String token = service.decryptToken(c);
        return new Response(c.getId(), c.getName(), c.getProvider(), c.getModelType(), c.getBaseUrl(), c.getModelName(), !token.isBlank(), token.isBlank() ? "********" : "********", Boolean.TRUE.equals(c.getActive()));
    }

    public record Request(String name, String provider, String modelType, String baseUrl, String modelName, String token, boolean activate) {
    }

    public record Response(Long id, String name, String provider, String modelType, String baseUrl, String modelName,
                           boolean tokenConfigured, String maskedToken, boolean active) {
    }
}
