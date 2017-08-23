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
package io.github.tjheslin1.patterdale.metrics;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

public class MetricsUseCase {

    private final List<OracleSQLProbe> oracleSqlProbes;

    public MetricsUseCase(List<OracleSQLProbe> oracleSqlProbes) {
        this.oracleSqlProbes = oracleSqlProbes;
    }

    // TODO map to a new class which composes a ProbeDefinition + ProbeResult
    public Map<ProbeDefinition, ProbeResult> scrapeMetrics() {
        return oracleSqlProbes.stream()
                .collect(toMap(OracleSQLProbe::probeDefinition, OracleSQLProbe::probe));
    }
}
