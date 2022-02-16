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

import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.atum.web.api.NotFoundException
import za.co.absa.atum.web.api.service.BaseApiServiceTest.TestEntity
import za.co.absa.atum.web.dao.ApiModelDao
import za.co.absa.atum.web.model.BaseApiModel

import java.util.UUID
import scala.concurrent.Future
import scala.util.Random

object BaseApiServiceTest {
  case class TestEntity(id: Option[UUID], someField: Int) extends BaseApiModel {
    override def withId(uuid: UUID): BaseApiModel = this.copy(id = Some(uuid))
  }

  object TestEntity {
    def random(someField: Int = Random.nextInt()): TestEntity = TestEntity(id = Some(UUID.randomUUID()), someField)
  }

}

class BaseApiServiceTest extends AnyFlatSpec with ScalaFutures with PatienceConfiguration
  with Matchers with IdiomaticMockito with BeforeAndAfterEach {

  private val mockedDao: ApiModelDao[TestEntity] = mock[ApiModelDao[TestEntity]]
  private val service = new BaseApiService[TestEntity](mockedDao) {
    override val entityName: String = "TestEntity"
  }

  override def beforeEach(): Unit = {
    Mockito.reset(mockedDao) // this allows verify to always count from fresh 0 in each test
  }

  private val existingId1 = UUID.randomUUID()
  private val expectedEntity1 = TestEntity(id = Some(existingId1), someField = 42)
  private val notExistingId1 = UUID.randomUUID()

  "BaseApiService" should "yield an entity via getById(validId) " in {
    when(mockedDao.getById(existingId1)).thenReturn(Future.successful(Some(expectedEntity1)))
    whenReady(service.getById(existingId1)) {
      _ shouldBe Some(expectedEntity1)
    }
    verify(mockedDao, times(1)).getById(existingId1)
  }

  it should "yield no entity via getById(invalidId)" in {
    when(mockedDao.getById(existingId1)).thenReturn(Future.successful(None))
    whenReady(service.getById(existingId1)) {
      _ shouldBe None
    }
    verify(mockedDao, times(1)).getById(existingId1)
  }

  it should "yield list of entities" in {
    val someEntities = (1 to 5).map(TestEntity.random).toList
    val someFilter: TestEntity => Boolean = _ => true

    when(mockedDao.getList(limit = 5, offset = 2, someFilter)).thenReturn(Future.successful(someEntities))
    whenReady(service.getList(5,2, someFilter)) {
      _ shouldBe someEntities
    }
    verify(mockedDao, times(1)).getList(limit = 5, offset = 2, someFilter)
  }

  it should "add an entity" in {
    val blankEntity = TestEntity(None, 17)
    val assignedId = UUID.randomUUID()

    when(mockedDao.add(blankEntity)).thenReturn(Future.successful(assignedId))
    whenReady(service.add(blankEntity)) {
      _ shouldBe assignedId
    }
    verify(mockedDao, times(1)).add(blankEntity)
  }

  it should "update an entity" in {
    when(mockedDao.update(expectedEntity1)).thenReturn(Future.successful(true))
    whenReady(service.update(expectedEntity1)) {
      _ shouldBe true
    }
    verify(mockedDao, times(1)).update(expectedEntity1)
  }

  it should "entity exists" in {
    when(mockedDao.getById(existingId1)).thenReturn(Future.successful(Some(expectedEntity1)))
    when(mockedDao.getById(notExistingId1)).thenReturn(Future.successful(None))

    import scala.concurrent.ExecutionContext.Implicits.global
    val checks = Future.sequence(Seq(service.exists(existingId1), service.exists(notExistingId1)))
    whenReady(checks) { _ shouldBe Seq(true, false)}
    verify(mockedDao, times(1)).getById(existingId1)
    verify(mockedDao, times(1)).getById(notExistingId1)
  }

  it should "should process withExistingEntityF correctly" in {
    when(mockedDao.getById(existingId1)).thenReturn(Future.successful(Some(expectedEntity1)))
    when(mockedDao.getById(notExistingId1)).thenReturn(Future.successful(None))

    val mockedFfn: TestEntity => Future[Boolean] = mock[TestEntity => Future[Boolean]]
    when(mockedFfn(expectedEntity1)).thenReturn(Future.successful(true))

    whenReady(service.withExistingEntityF(existingId1)(mockedFfn)) {
      _ shouldBe true
    }

    whenReady(service.withExistingEntityF(notExistingId1)(mockedFfn).failed) { exception =>
      exception shouldBe a[NotFoundException]
      exception.getMessage shouldBe s"TestEntity referenced by id=${notExistingId1.toString} was not found."
    }

    verify(mockedDao, times(1)).getById(existingId1)
    verify(mockedDao, times(1)).getById(notExistingId1)
  }

}
