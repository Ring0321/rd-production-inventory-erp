package com.ruiding.erp.service.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "rd.erp.ai")
public class RdAiProperties {
    private boolean enabled;
    private String apiKey = "";
    private String model = "";
    private boolean costApproved;
    private int dailyCallLimit;
    private int perUserMinuteLimit = 2;
    private int maxOutputTokens = 512;

    public String state() {
        if (!enabled) return "DISABLED";
        if (apiKey == null || apiKey.isBlank() || apiKey.length() > 512 || apiKey.matches("(?s).*\\s.*")
                || model == null || !model.matches("glm-[a-z0-9][a-z0-9.-]{0,63}")) return "UNCONFIGURED";
        if (!costApproved) return "COST_NOT_APPROVED";
        if (dailyCallLimit < 1 || dailyCallLimit > 1000 || perUserMinuteLimit < 1 || perUserMinuteLimit > 10
                || maxOutputTokens < 128 || maxOutputTokens > 1024) return "LIMIT_NOT_SET";
        return "READY";
    }
}
