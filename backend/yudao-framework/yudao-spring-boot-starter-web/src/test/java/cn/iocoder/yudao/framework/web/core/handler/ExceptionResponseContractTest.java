package cn.iocoder.yudao.framework.web.core.handler;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExceptionResponseContractTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebTestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void businessErrorKeepsItsCodeAndMessageInJsonResponse() throws Exception {
        mvc.perform(post("/contract/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1_080_530_012))
                .andExpect(jsonPath("$.msg").value("扫描包码不属于当前有效装车任务"));
    }

    @Test
    void missingParameterUsesTheSameJsonErrorEnvelope() throws Exception {
        mvc.perform(get("/contract/task"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请求参数缺失:taskId"));
    }

    @Configuration
    @EnableWebMvc
    static class WebTestConfig {
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler("exception-contract-test", null);
        }

        @Bean
        ContractController contractController() {
            return new ContractController();
        }
    }

    @RestController
    static class ContractController {
        @PostMapping("/contract/preview")
        Object preview() {
            throw new ServiceException(1_080_530_012, "扫描包码不属于当前有效装车任务");
        }

        @GetMapping("/contract/task")
        Long task(@RequestParam("taskId") Long taskId) {
            return taskId;
        }
    }
}
