package com.ruiding.erp.controller.admin.packageunit;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruiding.erp.service.packageunit.RdDigitalLinkService;
import com.ruiding.erp.service.packageunit.RdPackagePassportService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.util.MultiValueMap;
import static com.ruiding.erp.enums.ErrorCodeConstants.PACKAGE_NOT_EXISTS;

@RestController
@RequestMapping("/rd/package-link")
public class RdPackageLinkController {
    @Resource private RdPackagePassportService passports;
    @Resource private RdDigitalLinkService links;
    private final ObjectMapper json = new ObjectMapper();

    @GetMapping("/{code}/resolve") @PermitAll
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public ResponseEntity<byte[]> resolve(@PathVariable String code,
            @RequestParam MultiValueMap<String, String> query,
            @RequestHeader(value = "Accept", required = false) String accept,
            @RequestHeader(value = "If-None-Match", required = false) String previous,
            @RequestHeader(value = "X-Rd-Web-Transport", required = false) String transport) {
        try {
            RdDigitalLinkService.checkedCode(code);
            List<String> explicit = query.get("resource");
            String kind;
            if (explicit != null) {
                if (explicit.size() != 1 || !Set.of("web", "passport", "document", "links").contains(explicit.get(0))) return problem(400, "Unsupported package representation");
                kind = explicit.get(0);
            } else kind = selectRepresentation(accept);
            if (kind == null) return problem(406, "Unsupported package representation");
            if (!kind.equals("web")) return resource(code, kind, previous);
            if ("nginx".equals(transport)) {
                return ResponseEntity.ok().header("X-Accel-Redirect", "/_rd-package-web")
                        .header("Cache-Control", "no-store").header("Vary", "Accept").body(new byte[0]);
            }
            String target = links.build(code) + "?resource=web";
            if ("pda".equals(query.getFirst("workMode"))) target += "&workMode=pda";
            if ("1".equals(query.getFirst("internal"))) target += "&internal=1";
            return ResponseEntity.status(302).header("Location", target).header("Cache-Control", "no-store")
                    .header("Vary", "Accept").body(new byte[0]);
        } catch (IllegalArgumentException e) { return problem(400, "Invalid package representation"); }
    }

    static String selectRepresentation(String accept) {
        if (accept == null || accept.isBlank()) return "web";
        var ranges = MediaType.parseMediaTypes(accept);
        var available = List.of("text/html", "application/linkset+json", "application/json", "application/ld+json");
        double best = 0; String kind = null;
        for (int i = 0; i < available.size(); i++) {
            MediaType type = MediaType.parseMediaType(available.get(i));
            var matching = new ArrayList<MediaType>();
            for (var range : ranges) if (range.includes(type)) matching.add(range);
            matching.sort(Comparator.comparingInt((MediaType m) -> m.isWildcardType() ? 0 : m.isWildcardSubtype() ? 1 : 2).reversed());
            double quality = matching.isEmpty() ? 0 : matching.get(0).getQualityValue();
            if (quality > best) { best = quality; kind = List.of("web", "links", "passport", "document").get(i); }
        }
        return kind;
    }

    @GetMapping("/{code}/{resource}") @PermitAll
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public ResponseEntity<byte[]> resource(@PathVariable String code, @PathVariable String resource,
            @RequestHeader(value = "If-None-Match", required = false) String previous) {
        if (!Set.of("passport", "document", "links").contains(resource)) return problem(404, "Resource not found");
        try {
            var data = passports.read(code);
            Object body = switch (resource) {
                case "links" -> data.linkset();
                case "document" -> data.document();
                default -> data.passport();
            };
            byte[] bytes = json.writeValueAsBytes(body);
            String digest = resource.equals("links") ? java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) : data.passport().digest();
            String tag = "W/\"" + resource + "-" + digest + "\"";
            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl("no-store");
            headers.setETag(tag);
            headers.set("X-Content-Type-Options", "nosniff");
            headers.setVary(java.util.List.of("Accept", "tenant-id"));
            headers.set("Link", "<" + links.representation(code, "links") + ">; rel=\"linkset\"; type=\"application/linkset+json\"");
            if (previous != null && java.util.Arrays.stream(previous.split(",")).map(String::trim).anyMatch(t -> t.equals(tag) || t.equals("*"))) {
                return new ResponseEntity<>(null, headers, HttpStatus.NOT_MODIFIED);
            }
            String mime = switch (resource) {
                case "links" -> "application/linkset+json";
                case "document" -> "application/ld+json";
                default -> "application/json";
            };
            headers.setContentType(MediaType.parseMediaType(mime + ";charset=UTF-8"));
            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (ServiceException e) {
            return problem(e.getCode().equals(PACKAGE_NOT_EXISTS.getCode()) ? 404 : 503, "Public package source unavailable");
        } catch (IllegalArgumentException e) { return problem(400, "Invalid package link");
        } catch (Exception e) { return problem(503, "Public package source unavailable"); }
    }

    private static ResponseEntity<byte[]> problem(int code, String detail) {
        String body = "{\"type\":\"about:blank\",\"status\":" + code + ",\"title\":\"" + detail + "\"}";
        return ResponseEntity.status(code).contentType(MediaType.parseMediaType("application/problem+json"))
                .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
