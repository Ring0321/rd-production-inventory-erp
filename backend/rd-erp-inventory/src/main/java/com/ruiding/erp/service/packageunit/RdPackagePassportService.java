package com.ruiding.erp.service.packageunit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceRespVO;
import com.ruiding.erp.enums.RdPackageStatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.ruiding.erp.enums.ErrorCodeConstants.PACKAGE_NOT_EXISTS;

@Service
public class RdPackagePassportService {
    public static final String PROFILE = "DUOXU_PUBLIC_PACKAGE_V1";
    public static final int MAX_BYTES = 1048576;
    @Resource private RdPackageUnitService packages;
    @Resource private RdDigitalLinkService links;
    private final ObjectMapper json = new ObjectMapper();
    public record Passport(String schema, String algorithm, String digest, String generatedAt, String canonicalJson) { }
    public record Resources(Passport passport, Map<String, Object> document, Map<String, Object> linkset) { }

    @Transactional(readOnly = true)
    public Resources read(String value) {
        TenantContextHolder.getRequiredTenantId();
        String code = RdDigitalLinkService.checkedCode(value);
        var trace = packages.getTraceDetail(code, false);
        if (trace == null) throw exception(PACKAGE_NOT_EXISTS);
        if (!code.equals(trace.getPackageCode()) || !Boolean.FALSE.equals(trace.getInternalView())) {
            throw new IllegalStateException("Public package source is unavailable");
        }
        String identity = RdDigitalLinkService.checkedIdentity(code, trace.getDigitalLink());
        String stage = Arrays.stream(RdPackageStatusEnum.values()).map(RdPackageStatusEnum::getStatus)
                .filter(s -> s.equals(trace.getStatus())).findFirst().orElse("UNKNOWN");
        String quality = Set.of("DRAFT", "SUBMITTED", "CORRECTED", "RESUBMITTED", "PASSED", "REJECTED")
                .contains(Objects.toString(trace.getQualityStatus(), "")) ? trace.getQualityStatus() : "UNKNOWN";
        String conclusion = confirmedQuality(trace, quality);
        var events = new ArrayList<Map<String, Object>>();
        if (trace.getEvents() != null) {
            trace.getEvents().stream().filter(Objects::nonNull)
                .filter(e -> Set.of("PRODUCTION", "LABEL", "QUALITY", "STOCK").contains(Objects.toString(e.getStage(), "")))
                .filter(e -> e.getEventTime() != null)
                .sorted(Comparator.comparing((com.ruiding.erp.controller.admin.packageunit.vo.RdPackageTraceEventRespVO e) -> e.getEventTime())
                        .thenComparing(e -> e.getStage()))
                .forEach(e -> events.add(fields("stage", e.getStage(), "eventTime", e.getEventTime().toString(), "timeBasis", "ERP_LOCAL_DATETIME")));
        }
        // Explicit public projection: never serialize a trace DTO, private fields, titles or free event details.
        Map<String, Object> document = fields("@context", fields("@vocab", "urn:duoxu:public-package:v1:"),
                "@type", "PackagePassport", "@id", identity, "profile", PROFILE, "packageCode", code,
                "product", fields("name", trace.getProductName(), "model", trace.getProductModel(),
                    "furnaceNo", trace.getFurnaceNo(), "furnaceLayer", trace.getFurnaceLayer(),
                    "weightKg", trace.getPackageWeight() == null ? null : trace.getPackageWeight().toPlainString(),
                    "productionDate", trace.getProductionDate() == null ? null : trace.getProductionDate().toString(),
                    "furnaceOutDate", trace.getFurnaceOutDate() == null ? null : trace.getFurnaceOutDate().toString(), "shift", trace.getShiftCode()),
                "workflow", fields("status", stage, "qualityState", quality, "confirmedQuality", conclusion),
                "events", List.copyOf(events));
        Map<String, Object> directory = fields("linkset", List.of(fields("anchor", identity,
                "self", List.of(target(links.build(code), "text/html", "公开/按权限追溯页面")),
                "alternate", List.of(target(links.representation(code, "passport"), "application/json", "公开护照快照"),
                                     target(links.representation(code, "document"), "application/ld+json", "公开机器文档")),
                "linkset", List.of(target(links.representation(code, "links"), "application/linkset+json", "公开资源目录")))));
        try {
            String canonical = json.writeValueAsString(document);
            byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Public snapshot exceeds size limit");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new Resources(new Passport(PROFILE, "SHA-256", digest, Instant.now().toString(), canonical), document, directory);
        } catch (IllegalArgumentException e) { throw e;
        } catch (Exception e) { throw new IllegalStateException("Unable to produce public package snapshot"); }
    }

    private static String confirmedQuality(RdPackageTraceRespVO trace, String stage) {
        String raw = Objects.toString(trace.getQualityConclusion(), "").toUpperCase(Locale.ROOT);
        if ("PASSED".equals(stage) && Set.of("PASS", "PASSED").contains(raw)) return "PASSED";
        if ("REJECTED".equals(stage) && Set.of("REJECT", "REJECTED").contains(raw)) return "REJECTED";
        return null;
    }
    private static Map<String, Object> target(String href, String type, String title) {
        return fields("href", href, "type", type, "title*", List.of(fields("value", title, "language", "zh-CN")));
    }
    private static Map<String, Object> fields(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return Collections.unmodifiableMap(result);
    }
}
