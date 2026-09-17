package com.wardrobe.agent.tryon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(TryOnTaskDispatcherTest.TestConfig.class)
class TryOnTaskDispatcherTest {
    @Autowired private ApplicationEventPublisher events;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private TryOnWorker worker;

    @BeforeEach
    void resetWorker() {
        reset(worker);
    }

    @Test
    void dispatchesWorkerOnlyAfterTransactionCommits() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new TryOnTaskSubmittedEvent("task-commit"));
            verifyNoInteractions(worker);
        });

        verify(worker).start("task-commit");
    }

    @Test
    void doesNotDispatchWorkerWhenTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new TryOnTaskSubmittedEvent("task-rollback"));
            status.setRollbackOnly();
        });

        verifyNoInteractions(worker);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TryOnWorker worker() {
            return mock(TryOnWorker.class);
        }

        @Bean
        TryOnTaskDispatcher dispatcher(TryOnWorker worker) {
            return new TryOnTaskDispatcher(worker);
        }
    }
}
