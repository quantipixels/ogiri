// SPDX-License-Identifier: Apache-2.0
package com.quantipixels.ogiri.spring;

import static org.assertj.core.api.Assertions.assertThat;
import com.quantipixels.ogiri.*;
import java.time.Duration;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.*;

class OgiriAutoConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OgiriAutoConfiguration.class))
            .withPropertyValues("ogiri.enabled=true")
            .withBean(DataSource.class, () -> new DriverManagerDataSource(System.getenv("OGIRI_TEST_JDBC_URL"),
                    System.getenv("OGIRI_TEST_JDBC_USER"), System.getenv("OGIRI_TEST_JDBC_PASSWORD")))
            .withBean(UserDetailsService.class, () -> new InMemoryUserDetailsManager(
                    User.withUsername("test").password("{noop}test").roles("USER").build()));

    @Test void disabledStarterAddsNoSecurityOrStorageBeans() {
        runner.withPropertyValues("ogiri.enabled=false").run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(JdbcSessions.class).doesNotHaveBean(OgiriSecurity.class);
        });
    }

    @Test void starterIsInactiveUntilEnabled() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(OgiriAutoConfiguration.class))
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(JdbcSessions.class));
    }

    @Test void missingDataSourceHasAnActionableFailure() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(OgiriAutoConfiguration.class))
                .withPropertyValues("ogiri.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void boundPolicyAndCustomAuthenticationBeansWin() {
        var manager = (org.springframework.security.authentication.AuthenticationManager) authentication -> authentication;
        var encoder = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        runner.withPropertyValues("ogiri.lifetime=12h", "ogiri.maximum-sessions=3", "ogiri.base-path=/api/login")
                .withBean(org.springframework.security.authentication.AuthenticationManager.class, () -> manager)
                .withBean(PasswordEncoder.class, () -> encoder).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SessionPolicy.class)).isEqualTo(new SessionPolicy(Duration.ofHours(12), 3));
                    assertThat(context.getBean(PasswordEncoder.class)).isSameAs(encoder);
                    assertThat(context.getBean(org.springframework.security.authentication.AuthenticationManager.class)).isSameAs(manager);
                });
    }

    @Test void invalidEndpointPathsFailAtBindingRatherThanOpenUnintendedRoutes() {
        runner.withPropertyValues("ogiri.base-path=/auth/**").run(context -> assertThat(context).hasFailed());
    }

    @Test void customStorageWins() {
        var custom = new JdbcSessions(new DriverManagerDataSource(System.getenv("OGIRI_TEST_JDBC_URL"),
                System.getenv("OGIRI_TEST_JDBC_USER"), System.getenv("OGIRI_TEST_JDBC_PASSWORD")));
        runner.withBean(JdbcSessions.class, () -> custom)
                .run(context -> { assertThat(context).hasNotFailed(); assertThat(context.getBean(JdbcSessions.class)).isSameAs(custom); });
    }

    @Test void customAccountsDoNotRequireAUserDetailsService() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(OgiriAutoConfiguration.class))
                .withPropertyValues("ogiri.enabled=true", "ogiri.endpoints-enabled=false")
                .withBean(DataSource.class, () -> new DriverManagerDataSource(System.getenv("OGIRI_TEST_JDBC_URL"),
                        System.getenv("OGIRI_TEST_JDBC_USER"), System.getenv("OGIRI_TEST_JDBC_PASSWORD")))
                .withBean(OgiriAccounts.class, () -> new OgiriAccounts() {
                    public Subject subject(org.springframework.security.core.Authentication authentication) {
                        return new Subject("users", "", authentication.getName());
                    }
                    public UserDetails load(Subject subject) {
                        return User.withUsername(subject.subjectId()).password("unused").roles("USER").build();
                    }
                })
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JdbcSessions.class).hasSingleBean(OgiriAccounts.class));
    }

    @Test void multipleTransactionManagersRequireExplicitStorageWiring() {
        runner.withBean("firstManager", org.springframework.transaction.PlatformTransactionManager.class,
                        () -> new org.springframework.jdbc.support.JdbcTransactionManager(
                                new DriverManagerDataSource(System.getenv("OGIRI_TEST_JDBC_URL"),
                                        System.getenv("OGIRI_TEST_JDBC_USER"), System.getenv("OGIRI_TEST_JDBC_PASSWORD"))))
                .withBean("secondManager", org.springframework.transaction.PlatformTransactionManager.class,
                        () -> new org.springframework.jdbc.support.JdbcTransactionManager(
                                new DriverManagerDataSource(System.getenv("OGIRI_TEST_JDBC_URL"),
                                        System.getenv("OGIRI_TEST_JDBC_USER"), System.getenv("OGIRI_TEST_JDBC_PASSWORD"))))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage(
                            "Multiple transaction managers found; provide a JdbcSessions bean using the manager for Ogiri's DataSource");
                });
    }
}
