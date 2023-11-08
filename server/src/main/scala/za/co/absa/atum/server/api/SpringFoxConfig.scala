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

package za.co.absa.atum.server.api

import org.springframework.context.annotation.{Bean, Configuration}
import springfox.documentation.builders.{ApiInfoBuilder, PathSelectors, RequestHandlerSelectors}
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2
import za.co.absa.atum.server.api.SpringFoxConfig.apiInfo

@Configuration
@EnableSwagger2
class SpringFoxConfig {
  @Bean
  def api(): Docket = {
    new Docket(DocumentationType.SWAGGER_2)
      .apiInfo(apiInfo)
      .select()
      .apis(RequestHandlerSelectors.any())
      .paths(PathSelectors.regex("/api/.*"))
      .build()
  }
}

object SpringFoxConfig {
  private def apiInfo =
    new ApiInfoBuilder()
      .title("Atum API")
      .description("Atum API reference for developers")
      .license("Apache 2.0 License")
      .version("0.0.1")
      .build
}
