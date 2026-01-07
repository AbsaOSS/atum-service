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

package za.co.absa.atum.server.model.database

import za.co.absa.atum.model.dto.{AdditionalDataDTO, AdditionalDataItemDTO, AdditionalDataItemV2DTO}

case class AdditionalDataItemFromDB(
  adName: String,
  adValue: Option[String],
  author: String
)

object AdditionalDataItemFromDB {
//  def additionalDataFromDBItems(dbItems: Seq[AdditionalDataItemFromDB]): AdditionalDataDTO = {
//    AdditionalDataDTO(
//      dbItems
//        .map(item =>
//          item.adValue match {
//            case Some(value) => item.adName -> Some(AdditionalDataItemDTO(value, item.author))
//            case None => item.adName -> None
//          }
//        )
////        .toMap
//    )
//  }

  def toSeqAdditionalDataItemV2DTO(dbItems: Seq[AdditionalDataItemFromDB]): Seq[AdditionalDataItemV2DTO] = {
    dbItems.map(item =>
      AdditionalDataItemV2DTO(
        key = item.adName,
        value = item.adValue,
        author = item.author
      )
    )
  }
}
