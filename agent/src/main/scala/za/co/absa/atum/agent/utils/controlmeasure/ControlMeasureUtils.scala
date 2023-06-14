/*
 * Copyright 2018 ABSA Group Limited
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

package za.co.absa.atum.agent.utils.controlmeasure

import org.apache.spark.sql.DataFrame

/** This object contains utilities used in Control Measurements processing
  */

object ControlMeasureUtils {

  /** The method generates a temporary column name which does not exist in the specified `DataFrame`.
    *
    * @return
    *   An column name as a string
    */
  def getTemporaryColumnName(
      df: DataFrame,
      namePrefix: String = "tmp"
  ): String = {
    val r = scala.util.Random
    var tempColumnName = ""
    do {
      tempColumnName = s"${namePrefix}_${r.nextInt(10000).toString}"
    } while (df.schema.fields.exists(field =>
      field.name.compareToIgnoreCase(tempColumnName) == 0
    ))
    tempColumnName
  }

}
