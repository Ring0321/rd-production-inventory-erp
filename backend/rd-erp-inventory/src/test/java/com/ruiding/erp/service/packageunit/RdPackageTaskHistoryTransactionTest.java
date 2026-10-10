package com.ruiding.erp.service.packageunit;

import com.ruiding.erp.dal.dataobject.packageunit.RdPackageTaskEventDO;
import com.ruiding.erp.dal.mysql.packageunit.RdPackageTaskEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RdPackageTaskHistoryTransactionTest {
    @Test
    void shouldRejectHistoryWrittenOutsideBusinessTransaction() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var service = context.getBean(RdPackageTaskHistoryService.class);
            assertThrows(IllegalTransactionStateException.class, () -> service.append(event()));
            verify(context.getBean(RdPackageTaskEventMapper.class), never()).insert(any(RdPackageTaskEventDO.class));
        }
    }

    @Test
    void shouldJoinExistingBusinessTransaction() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var service = context.getBean(RdPackageTaskHistoryService.class);
            var event = event();
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class))
                    .executeWithoutResult(status -> service.append(event));
            verify(context.getBean(RdPackageTaskEventMapper.class)).insert(event);
        }
    }

    private static RdPackageTaskEventDO event() {
        return new RdPackageTaskEventDO().setPackageId(1L).setPackageCode("TEST-PACKAGE")
                .setStage("LOADING").setEventCode("ASSIGNED").setSourceType("LOADING_TASK")
                .setSourceId(2L).setSourceNo("TEST-TASK").setSourceVersion(0).setEventTime(LocalDateTime.now());
    }

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:rd_e5_transaction;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
        @Bean RdPackageTaskEventMapper eventMapper() { return mock(RdPackageTaskEventMapper.class); }
        @Bean RdPackageTaskHistoryService historyService() { return new RdPackageTaskHistoryService(); }
    }
}
