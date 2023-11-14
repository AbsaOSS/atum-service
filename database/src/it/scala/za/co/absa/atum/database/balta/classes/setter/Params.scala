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

package za.co.absa.atum.database.balta.classes.setter

import scala.collection.immutable.ListMap

sealed abstract class Params private(items: ListMap[String, SetterFnc]) {
  def keys: Option[List[String]]
  def setters: List[SetterFnc] = items.values.toList

  def apply(paramName: String): SetterFnc = {
    items(paramName)
  }

  def size: Int = items.size

}
object Params {

  def add[T: AllowedParamTypes](paramName: String, value: T): NamedParams = {
    new NamedParams().add(paramName, value)
  }

  def addNull(paramName: String): NamedParams = {
    new NamedParams().addNull(paramName)
  }

  def add[T: AllowedParamTypes](value: T): OrderedParams = {
    new OrderedParams().add(value)
  }

  def addNull[T: AllowedParamTypes](): OrderedParams = {
    new OrderedParams().addNull()
  }

  sealed class NamedParams private[setter](items: ListMap[String, SetterFnc] = ListMap.empty) extends Params(items) {
    def add[T: AllowedParamTypes](paramName: String, value: T): NamedParams = {
      val setter = SetterFnc.createSetterFnc(value)
      new NamedParams(items + (paramName, setter)) // TODO https://github.com/AbsaOSS/balta/issues/1
    }

    def addNull(paramName: String): NamedParams = {
      val setter = SetterFnc.nullSetterFnc
      new NamedParams(items + (paramName, setter)) // TODO https://github.com/AbsaOSS/balta/issues/1
    }

    def pairs: List[(String, SetterFnc)] = items.toList

    override def keys: Option[List[String]] = Some(items.keys.toList)
  }

  sealed class OrderedParams private[setter](items: ListMap[String, SetterFnc] = ListMap.empty) extends Params(items) {
    def add[T: AllowedParamTypes](value: T): OrderedParams = {
      val key = items.size.toString
      val setter = SetterFnc.createSetterFnc(value)
      new OrderedParams(items + (key, setter))
    }

    def addNull[T: AllowedParamTypes](): OrderedParams = {
      val key = items.size.toString
      val setter = SetterFnc.nullSetterFnc
      new OrderedParams(items + (key, setter))
    }

    override val keys: Option[List[String]] = None
  }
}
