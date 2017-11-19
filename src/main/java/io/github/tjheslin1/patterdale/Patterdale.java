/*
 * Copyright 2017 Thomas Heslin <tjheslin1@gmail.com>.
 *
 * This file is part of Patterdale-jvm.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.tjheslin1.patterdale;

import io.github.tjheslin1.patterdale.config.ConfigUnmarshaller;
import io.github.tjheslin1.patterdale.config.Passwords;
import io.github.tjheslin1.patterdale.config.PasswordsUnmarshaller;
import io.github.tjheslin1.patterdale.config.PatterdaleConfig;
import io.github.tjheslin1.patterdale.database.DBConnectionPool;
import io.github.tjheslin1.patterdale.database.hikari.HikariDBConnection;
import io.github.tjheslin1.patterdale.database.hikari.HikariDBConnectionPool;
import io.github.tjheslin1.patterdale.http.WebServer;
import io.github.tjheslin1.patterdale.http.jetty.JettyWebServerBuilder;
import io.github.tjheslin1.patterdale.metrics.MetricsUseCase;
import io.github.tjheslin1.patterdale.metrics.probe.DatabaseDefinition;
import io.github.tjheslin1.patterdale.metrics.probe.OracleSQLProbe;
import io.github.tjheslin1.patterdale.metrics.probe.Probe;
import io.github.tjheslin1.patterdale.metrics.probe.TypeToProbeMapper;
import io.prometheus.client.CollectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.tjheslin1.patterdale.PatterdaleRuntimeParameters.patterdaleRuntimeParameters;
import static io.github.tjheslin1.patterdale.database.hikari.HikariDataSourceProvider.retriableDataSource;
import static io.github.tjheslin1.patterdale.infrastructure.RegisterExporters.serverWithStatisticsCollection;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class Patterdale {

    private final PatterdaleRuntimeParameters runtimeParameters;
    private final Map<String, DBConnectionPool> connectionPools;
    private final TypeToProbeMapper typeToProbeMapper;
    private final Map<String, Probe> probesByName;
    private final Logger logger;

    public Patterdale(PatterdaleRuntimeParameters runtimeParameters, Map<String, DBConnectionPool> connectionPools, TypeToProbeMapper typeToProbeMapper, Map<String, Probe> probesByName, Logger logger) {
        this.runtimeParameters = runtimeParameters;
        this.connectionPools = connectionPools;
        this.typeToProbeMapper = typeToProbeMapper;
        this.probesByName = probesByName;
        this.logger = logger;
    }

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger("application");

        PatterdaleConfig patterdaleConfig = new ConfigUnmarshaller(logger)
                .parseConfig(new File(System.getProperty("config.file")));

        Passwords passwords = new PasswordsUnmarshaller(logger)
                .parsePasswords(new File(System.getProperty("passwords.file")));

        PatterdaleRuntimeParameters runtimeParameters = patterdaleRuntimeParameters(patterdaleConfig);

        Map<String, DBConnectionPool> connectionPools = runtimeParameters.databases().stream()
                .collect(Collectors.toMap(databaseDefinition -> databaseDefinition.name,
                        databaseDefinition -> connectionPool(logger, passwords, runtimeParameters, databaseDefinition)));

        Map<String, Probe> probesByName = runtimeParameters.probes().stream()
                .collect(toMap(probe -> probe.name, probe -> probe));

        logger.debug("starting Patterdale!");
        new Patterdale(runtimeParameters, connectionPools, new TypeToProbeMapper(logger), probesByName, logger)
                .start();
    }

    public void start() {
        logger.info("logback.configurationFile = " + System.getProperty("logback.configurationFile"));
        CollectorRegistry registry = new CollectorRegistry();

        List<OracleSQLProbe> probes = runtimeParameters.databases().stream()
                .flatMap(this::createProbes)
                .collect(toList());

        long cacheDuration = Math.max(runtimeParameters.cacheDuration(), 1);
        logger.info(format("Using database scrape cache duration of '%d' seconds.", cacheDuration));

        WebServer webServer = new JettyWebServerBuilder(logger)
                .withServer(serverWithStatisticsCollection(registry, runtimeParameters.httpPort()))
                .registerMetricsEndpoint("/metrics", new MetricsUseCase(probes), runtimeParameters, registry, cacheDuration)
                .build();

        try {
            webServer.start();
            logger.info("Web server started successfully at " + webServer.baseUrl());
        } catch (Exception e) {
            logger.error("Error occurred starting Jetty Web Server.", e);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                webServer.stop();
                logger.info("Shutting down.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }

    private static HikariDBConnectionPool connectionPool(Logger logger, Passwords passwords, PatterdaleRuntimeParameters runtimeParameters, DatabaseDefinition databaseDefinition) {
        return new HikariDBConnectionPool(new HikariDBConnection(retriableDataSource
                (runtimeParameters, databaseDefinition, passwords, logger)));
    }

    private Stream<OracleSQLProbe> createProbes(DatabaseDefinition databaseDefinition) {
        return Arrays.stream(databaseDefinition.probes)
                .map(probeName -> typeToProbeMapper.createProbe(databaseDefinition.name, connectionPools.get(databaseDefinition.name), probesByName.get(probeName)));
    }

}
