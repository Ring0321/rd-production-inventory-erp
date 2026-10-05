package cn.iocoder.yudao.framework.jackson.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessDateJsonContractTest {

    @Test
    void webBuilderWritesBusinessDateAsIsoAndKeepsEventTimestamp() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new YudaoJacksonAutoConfiguration().ldtEpochMillisCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();
        assertDateContract(mapper);
    }

    @Test
    void moduleRegistrationUsesTheSameDateContract() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new YudaoJacksonAutoConfiguration().timestampSupportModuleBean());
        assertDateContract(mapper);
    }

    private void assertDateContract(ObjectMapper mapper) throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 5);
        LocalDateTime event = date.atTime(8, 30, 15);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(
                Map.of("bizDate", date, "eventTime", event)));
        assertTrue(json.get("bizDate").isTextual(), "Business date must not be a JSON array");
        assertEquals("2026-10-05", json.get("bizDate").asText());
        assertTrue(json.get("eventTime").isIntegralNumber(), "Event timestamp format must stay compatible");
        assertEquals(date, mapper.readValue(json.get("bizDate").toString(), LocalDate.class));
        assertEquals(event, mapper.readValue(json.get("eventTime").toString(), LocalDateTime.class));
    }
}
