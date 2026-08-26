package com.harness.core.controller;

import com.harness.core.entity.ModelConfig;
import com.harness.core.service.AiRuntimeModelProvider;
import com.harness.core.service.AiServiceFactory;
import com.harness.core.service.ModelConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/model-configs")
public class ModelConfigController {
    private final ModelConfigService service;
    private final AiRuntimeModelProvider provider;
    private final AiServiceFactory factory;

    public ModelConfigController(ModelConfigService service, AiRuntimeModelProvider provider, AiServiceFactory factory) {
        this.service = service;
        this.provider = provider;
        this.factory = factory;
    }

    @GetMapping
    public List<Response> list() {
        return service.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/active")
    public Response active() {
        return toResponse(service.activeOrFallback());
    }

    @PostMapping
    public Response create(@RequestBody Request req) {
        ModelConfig c = service.create(req.name(), req.baseUrl(), req.modelName(), req.token(), req.activate());
        refreshIfActive(c);
        return toResponse(c);
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @RequestBody Request req) {
        ModelConfig c = service.update(id, req.name(), req.baseUrl(), req.modelName(), req.token());
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
        return new Response(c.getId(), c.getName(), c.getProvider(), c.getBaseUrl(), c.getModelName(), !token.isBlank(), token.isBlank() ? "" : "••••••••", Boolean.TRUE.equals(c.getActive()));
    }

    public record Request(String name, String baseUrl, String modelName, String token, boolean activate) {
    }

    public record Response(Long id, String name, String provider, String baseUrl, String modelName,
                           boolean tokenConfigured, String maskedToken, boolean active) {
    }
}
