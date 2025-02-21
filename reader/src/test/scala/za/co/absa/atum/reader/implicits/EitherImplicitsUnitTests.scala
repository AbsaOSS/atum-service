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

package za.co.absa.atum.reader.implicits

import org.scalatest.funsuite.AnyFunSuiteLike
import sttp.client3.Identity
import sttp.client3.monad.IdMonad
import sttp.monad.MonadError
import za.co.absa.atum.reader.implicits.EitherImplicits.EitherMonadEnhancements

class EitherImplicitsUnitTests extends AnyFunSuiteLike {
  private implicit val monad: MonadError[Identity] = IdMonad

  test("EitherMonadEnhancements should project Right") {
    def fnc(b: Int): Identity[Either[String, String]] = Right(b.toString)
    val either = Right(1)
    val result = either.project(fnc)
    assert(result == Right("1"))
  }

  test("EitherMonadEnhancements should not project Left") {
    def fnc(b: Int): Identity[Either[Exception, String]] = Right(b.toString)
    val either = Left(new Exception("error"))
    val result = either.project(fnc)
    assert(result == either)
  }
}
