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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

public class ConfigUnmarshaller {

    private final Logger logger;

    public ConfigUnmarshaller(Logger logger) {
        this.logger = logger;
    }

    public PatterdaleConfig parseConfig(File configFile) {
        PatterdaleConfig config;
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            config = mapper.readValue(configFile, PatterdaleConfig.class);
            return config;
        } catch (IOException e) {
            logger.error("Failed to parse provided 'config.file'.", e);
            throw new IllegalStateException(e);
        }
    }
}
