package com.ruiding.erp.service.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "rd.erp.ai")
public class RdAiProperties {
    private boolean enabled;
    private String apiKey = "";
    private String deepseekApiKey = "";
    private String model = "";
    private boolean costApproved;
    private int dailyCallLimit;
    private int perUserMinuteLimit = 2;
    private int maxOutputTokens = 512;
    private String provider = "GLM";
    private BigDecimal monthlyBudgetCny = BigDecimal.ZERO;
    private String priceValidUntil = "";

    public boolean deepSeek() { return "DEEPSEEK".equals(provider); }
    public String activeApiKey() { return deepSeek() ? deepseekApiKey : apiKey; }

    public int monthlyReservationLimit() {
        try {
            int cents = monthlyBudgetCny.movePointRight(2).intValueExact();
            return cents >= 10 && cents <= 100000 ? cents / 10 : 0;
        } catch (Exception e) { return 0; }
    }

    public String state() {
        if (!enabled) return "DISABLED";
        String key = activeApiKey();
        if (key == null || key.isBlank() || key.length() > 512 || key.matches("(?s).*\\s.*")
                || model == null || !("GLM".equals(provider) && model.matches("glm-[a-z0-9][a-z0-9.-]{0,63}")
                    || deepSeek() && "deepseek-flash".equals(model))) return "UNCONFIGURED";
        if (!costApproved) return "COST_NOT_APPROVED";
        if (dailyCallLimit < 1 || dailyCallLimit > 1000 || perUserMinuteLimit < 1 || perUserMinuteLimit > 10
                || maxOutputTokens < 128 || maxOutputTokens > 1024) return "LIMIT_NOT_SET";
        if (deepSeek()) {
            if (monthlyReservationLimit() == 0) return "BUDGET_NOT_SET";
            try {
                LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
                LocalDate until = LocalDate.parse(priceValidUntil);
                if (until.isBefore(today) || until.isAfter(today.plusDays(31))) return "PRICE_REVIEW_REQUIRED";
            } catch (Exception e) { return "PRICE_REVIEW_REQUIRED"; }
        }
        return "READY";
    }
}
