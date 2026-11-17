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

package za.co.absa.atum.database.lineage

import za.co.absa.balta.DBTestSuite
import za.co.absa.balta.classes.JsonBString
import za.co.absa.balta.classes.setter.CustomDBType

import java.util.UUID

/**
 * Shared DB fixture helpers for `lineage` Balta integration suites.
 */
private[lineage] trait LineageDBTestSupport { self: DBTestSuite =>

  protected val fncLink = "lineage.link_to_parents"
  protected val fncCreatePartitioning = "runs.create_partitioning"

  protected def partitioningJson(name: String): String =
    s"""{"version":1,"keys":["lk"],"keysToValuesMap":{"lk":"$name"}}"""

  protected def partitioningOf(name: String): JsonBString = JsonBString(partitioningJson(name))

  protected def uniq(prefix: String): String = s"$prefix-${UUID.randomUUID()}"

  protected def ids(xs: Seq[Any]): CustomDBType =
    CustomDBType(xs.map(x => if (x == null) "NULL" else x.toString).mkString("{", ",", "}"), "BIGINT[]")

  /** Seeds a partitioning via the v1 path (takes NO advisory lock); an optional parent is linked with it. */
  protected def seed(name: String, parent: Option[Long] = None): Long = {
    val call = function(fncCreatePartitioning)
      .setParam("i_partitioning", partitioningOf(name))
      .setParam("i_by_user", "Seeder")
    val withParent = parent match {
      case Some(p) => call.setParam("i_parent_partitioning_id", p)
      case None => call.setParamNull("i_parent_partitioning_id")
    }
    withParent.execute { qr =>
      assert(qr.hasNext)
      val row = qr.next()
      assert(row.getInt("status").exists(s => s == 11 || s == 12))
      row.getLong("id_partitioning").get
    }
  }

  protected def link(child: Long, parents: CustomDBType): (Int, String) = {
    function(fncLink)
      .setParam("i_child_id", child)
      .setParam("i_parent_ids", parents)
      .setParam("i_by_user", "Linker")
      .execute { qr =>
        assert(qr.hasNext)
        val row = qr.next()
        val result = (row.getInt("status").get, row.getString("status_text").get)
        assert(!qr.hasNext)
        result
      }
  }
}
