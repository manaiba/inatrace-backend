package com.abelium.inatrace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test: the whole Spring context wires up.
 *
 * <p>It catches the failures that stop the application before it serves a single request --
 * a bean that cannot be created, a broken entity mapping, a missing configuration property.
 * It needs no local {@code application.properties}: the test profile carries every property
 * the beans resolve at startup.
 *
 * <p>The database is a real MySQL, started from Docker by Testcontainers, so the entity model
 * is built by the same dialect and the same server version production uses. Hibernate creates
 * the schema from the entity model; the Flyway migrations are pointed at an empty location and
 * are covered separately, because they need a database that is still empty at startup.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ContextBootSmokeTest {

	/**
	 * Started once for the class and thrown away afterwards. {@code @ServiceConnection} feeds
	 * its url, username and password to Spring, which is why the test profile configures no
	 * datasource of its own.
	 */
	@Container
	@ServiceConnection
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

	@Autowired
	private ApplicationContext context;

	@Autowired
	private DataSource dataSource;

	@Test
	@DisplayName("the application context loads")
	void contextLoads() {
		assertNotNull(context, "application context should have been created");
	}

	@Test
	@DisplayName("the entity model maps cleanly and core beans are present")
	void coreBeansArePresent() {
		// Hibernate builds the schema from the entity model on startup, so reaching this point
		// already proves the mappings are consistent. Check the pieces the app cannot run without.
		assertNotNull(dataSource, "a DataSource should be configured");
		assertTrue(context.containsBean("entityManagerFactory"), "JPA should be wired up");
		assertTrue(context.getBeanNamesForType(org.flywaydb.core.Flyway.class).length > 0,
				"MigrationsConfiguration injects the Flyway bean, so it must exist");
	}
}
