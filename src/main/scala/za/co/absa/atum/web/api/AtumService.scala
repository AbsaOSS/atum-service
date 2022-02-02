/*
 * Copyright 2021 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.atum.web.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{ObjectMapper, SerializationFeature}
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.{Bean, Configuration, PropertySource}
import com.fasterxml.jackson.module.scala.DefaultScalaModule

@Configuration
@SpringBootApplication
@PropertySource(Array("classpath:application.properties"))
@ConfigurationPropertiesScan(Array("za.co.absa.atum.web.api.config"))
class AtumService extends SpringBootServletInitializer {
  override def configure(application: SpringApplicationBuilder): SpringApplicationBuilder =
    application.sources(classOf[AtumService])

  @Bean
  def objectMapper(): ObjectMapper = {
    new ObjectMapper()
      .registerModule(DefaultScalaModule)
      .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
  }
}

object AtumService extends App {
  SpringApplication.run(classOf[AtumService], args :_ *)
}
