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

package za.co.absa.atum.web.api.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import za.co.absa.atum.web.api.config.BaseConfig

@Service
class BaseService @Autowired()(baseConfig: BaseConfig) {
  def getMessage: String = {
    s"The service says: alfa '${baseConfig.someKey}'"
  }
}

object BaseService {
  private var context: ApplicationContext = null

  def getBean[T](beanClass: Class[T]): T = context.getBean(beanClass)
}