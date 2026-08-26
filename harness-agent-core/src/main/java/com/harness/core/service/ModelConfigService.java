package com.harness.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harness.core.entity.ModelConfig;
import com.harness.core.mapper.ModelConfigMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ModelConfigService {
    private final ModelConfigMapper mapper;
    private final String fallbackBaseUrl;
    private final String fallbackModel;
    private final String fallbackToken;
    private final byte[] encryptionKey;

    public ModelConfigService(ModelConfigMapper mapper,
                              @Value("${ai.openai.base-url}") String fallbackBaseUrl,
                              @Value("${ai.openai.model-name}") String fallbackModel,
                              @Value("${ai.openai.api-key:}") String fallbackToken,
                              @Value("${MODEL_CONFIG_ENCRYPTION_KEY:harness-dev-key-change-me}") String encryptionSecret) {
        this.mapper = mapper;
        this.fallbackBaseUrl = fallbackBaseUrl;
        this.fallbackModel = fallbackModel;
        this.fallbackToken = fallbackToken;
        this.encryptionKey = sha256(encryptionSecret);
    }

    public List<ModelConfig> list() {
        return mapper.selectList(new LambdaQueryWrapper<ModelConfig>().orderByDesc(ModelConfig::getActive).orderByAsc(ModelConfig::getId));
    }

    public ModelConfig get(Long id) {
        return mapper.selectById(id);
    }

    public ModelConfig activeOrFallback() {
        ModelConfig active = mapper.selectOne(new LambdaQueryWrapper<ModelConfig>().eq(ModelConfig::getActive, true).last("LIMIT 1"));
        if (active != null) return active;
        ModelConfig fallback = new ModelConfig();
        fallback.setId(0L);
        fallback.setName("环境变量默认配置");
        fallback.setProvider("openai");
        fallback.setBaseUrl(fallbackBaseUrl);
        fallback.setModelName(fallbackModel);
        fallback.setApiTokenCiphertext(encrypt(fallbackToken));
        fallback.setActive(true);
        return fallback;
    }

    @Transactional
    public ModelConfig create(String name, String baseUrl, String modelName, String token, boolean activate) {
        validate(name, baseUrl, modelName, token, true);
        ModelConfig config = new ModelConfig();
        config.setName(name.trim()); config.setProvider("openai"); config.setBaseUrl(normalizeUrl(baseUrl));
        config.setModelName(modelName.trim()); config.setApiTokenCiphertext(encrypt(token)); config.setActive(false);
        mapper.insert(config);
        if (activate) activate(config.getId());
        return mapper.selectById(config.getId());
    }

    @Transactional
    public ModelConfig update(Long id, String name, String baseUrl, String modelName, String token) {
        ModelConfig config = require(id);
        validate(name, baseUrl, modelName, token, token != null && !token.isBlank());
        config.setName(name.trim()); config.setBaseUrl(normalizeUrl(baseUrl)); config.setModelName(modelName.trim());
        if (token != null && !token.isBlank()) config.setApiTokenCiphertext(encrypt(token));
        mapper.updateById(config);
        return mapper.selectById(id);
    }

    @Transactional
    public ModelConfig activate(Long id) {
        ModelConfig config = require(id);
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ModelConfig>().set(ModelConfig::getActive, false));
        config.setActive(true);
        mapper.updateById(config);
        return config;
    }

    @Transactional
    public void delete(Long id) {
        ModelConfig config = require(id);
        if (Boolean.TRUE.equals(config.getActive())) throw new IllegalStateException("当前启用的配置不能删除，请先启用其他配置");
        mapper.deleteById(id);
    }

    public String decryptToken(ModelConfig config) { return decrypt(config.getApiTokenCiphertext()); }

    private ModelConfig require(Long id) {
        ModelConfig config = mapper.selectById(id);
        if (config == null) throw new IllegalArgumentException("模型配置不存在: " + id);
        return config;
    }

    private void validate(String name, String url, String model, String token, boolean tokenRequired) {
        if (name == null || name.isBlank() || name.length() > 80) throw new IllegalArgumentException("配置名称不能为空且不超过80字符");
        if (model == null || model.isBlank() || model.length() > 120) throw new IllegalArgumentException("模型名称不能为空且不超过120字符");
        if (tokenRequired && (token == null || token.isBlank())) throw new IllegalArgumentException("Token不能为空");
        if (token != null && token.length() > 4096) throw new IllegalArgumentException("Token过长");
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException("Base URL 必须是合法的 http(s) 地址");
            String host = uri.getHost().toLowerCase();
            if (host.equals("localhost") || host.equals("metadata.google.internal") || host.equals("169.254.169.254") || isPrivate(host))
                throw new IllegalArgumentException("Base URL 不允许指向本机或内网地址");
        } catch (IllegalArgumentException e) { throw e; }
    }

    private boolean isPrivate(String host) {
        try { return InetAddress.getByName(host).isAnyLocalAddress() || InetAddress.getByName(host).isLoopbackAddress() || InetAddress.getByName(host).isSiteLocalAddress(); }
        catch (UnknownHostException e) { return false; }
    }
    private String normalizeUrl(String url) { return url.trim().replaceAll("/+$", ""); }
    private byte[] sha256(String text) { try { return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String encrypt(String value) { if (value == null) return ""; try { javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); byte[] iv = new byte[12]; new java.security.SecureRandom().nextBytes(iv); c.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(encryptionKey, "AES"), new javax.crypto.spec.GCMParameterSpec(128, iv)); return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Token加密失败", e); } }
    private String decrypt(String value) { if (value == null || value.isBlank()) return ""; try { String[] p = value.split("\\.", 2); javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); c.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(encryptionKey, "AES"), new javax.crypto.spec.GCMParameterSpec(128, Base64.getDecoder().decode(p[0]))); return new String(c.doFinal(Base64.getDecoder().decode(p[1])), StandardCharsets.UTF_8); } catch (Exception e) { throw new IllegalStateException("Token解密失败", e); } }
}
