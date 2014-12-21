/**
 * Copyright (C) 2014-2015 Really Inc. <http://really.io>
 */
package io.really.model

import akka.actor.{ ActorRef, ActorSystem, PoisonPill, Props }
import akka.testkit.{ TestActorRef, TestProbe }
import akka.persistence.{ Update => PersistenceUpdate }
import io.really.CommandError.ParentNotFound
import io.really.Request.{ Update, Delete, Create }
import io.really.Result.{ UpdateResult, CreateResult }
import io.really.fixture.PersistentModelStoreFixture
import io.really.model.CollectionActor.{ GetExistenceState, GetState, State }
import io.really.model.persistent.{ PersistentModelStore }
import io.really.model.persistent.ModelRegistry.{ ModelOperation, CollectionActorMessage, ModelResult }
import io.really.protocol.{ UpdateCommand, UpdateOp, UpdateBody }
import io.really.CommandError.InvalidCommand
import io.really._
import org.scalatest.BeforeAndAfterAll
import play.api.libs.json.{ JsNumber, Json }

import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.Random

class CollectionActorSpec extends BaseActorSpec {

  override val globals = new CollectionTestReallyGlobals(config, system)

  case class UnsupportedCommand(r: R, cmd: String) extends RoutableToCollectionActor

  var modelRouterRef: ActorRef = _
  var modelPersistentActor: ActorRef = _
  val models: List[Model] = List(BaseActorSpec.userModel, BaseActorSpec.carModel,
    BaseActorSpec.companyModel, BaseActorSpec.authorModel, BaseActorSpec.postModel)

  override def beforeAll() {
    super.beforeAll()
    modelRouterRef = globals.modelRegistry
    modelPersistentActor = globals.persistentModelStore

    modelPersistentActor ! PersistentModelStore.UpdateModels(models)
    modelPersistentActor ! PersistentModelStoreFixture.GetState
    expectMsg(models)

    modelRouterRef ! PersistenceUpdate(await = true)
    modelRouterRef ! CollectionActorMessage.GetModel(BaseActorSpec.userModel.r, self)
    expectMsg(ModelResult.ModelObject(BaseActorSpec.userModel, List.empty))
  }

  //"CollectionActor" should "get ModelNotFound for any supported request for unknown model" in {
  //  val r = R / 'unknownModel / 123
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  val em = probe.expectMsgType[ModelResult]
  //  em should be(ModelResult.ModelNotFound)
  //}
  //
  //it should "get ObjectNotFound for uncreated yet objects" in {
  //  val obj = R / 'users / 123
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(GetState(obj), probe.ref)
  //  probe.expectMsg(CommandError.ObjectNotFound(obj))
  //}
  //
  //it should "should have the correct BucketID, PersistenceID and R" in {
  //  val obj = R / 'posts / 123
  //  val bucketID = Helpers.getBucketIDFromR(obj)
  //  val collectionActor = TestActorRef[CollectionActor](Props(new CollectionActor(globals)), bucketID).underlyingActor
  //  collectionActor.bucketID should be(bucketID)
  //  collectionActor.persistenceId should be(bucketID)
  //  collectionActor.r should be(obj.skeleton)
  //}
  //
  //it should "update the internal model state when it receives the ModelUpdated message" in {
  //  val r = R / 'users / globals.quickSand.nextId()
  //  val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "name" shouldBe JsString("Hatem AlSum")
  //  res.body \ "age" shouldBe JsNumber(30)
  //  res.body \ "_rev" shouldBe JsNumber(1)
  //
  //  val newUserModel = BaseActorSpec.userModel.copy(
  //    fields = BaseActorSpec.userModel.fields + ("address" -> ValueField("address", DataType.RString, None, None, true))
  //  )
  //
  //  modelPersistentActor ! PersistentModelStore.UpdateModels(List(newUserModel))
  //  modelPersistentActor ! PersistentModelStoreFixture.GetState
  //  expectMsg(List(newUserModel))
  //
  //  modelRouterRef ! PersistenceUpdate(await = true)
  //  expectMsg(ModelOperation.ModelUpdated(BaseActorSpec.userModel.r, newUserModel, List.empty))
  //
  //  val rx = R / 'users / 445
  //  val userObjx = Json.obj("name" -> "Ahmed Refaey", "age" -> 23)
  //  globals.collectionActor.tell(Create(ctx, rx, userObjx), probe.ref)
  //  probe.expectMsgType[CommandError.ModelValidationFailed]
  //  //revert the model changes to leave the test case env clean
  //  modelPersistentActor ! PersistentModelStore.UpdateModels(models)
  //  modelPersistentActor ! PersistentModelStoreFixture.GetState
  //  expectMsg(models)
  //
  //  modelRouterRef ! PersistenceUpdate(await = true)
  //  expectMsg(ModelOperation.ModelUpdated(BaseActorSpec.userModel.r, BaseActorSpec.userModel, List.empty))
  //}
  //
  //it should "invalidate the internal state when it receives the ModelDeleted message" in {
  //  val r = R / 'companies / globals.quickSand.nextId()
  //  val userObj = Json.obj("name" -> "Foo Bar", "employees" -> 43)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "name" shouldBe JsString("Foo Bar")
  //  //get company model
  //  modelRouterRef ! CollectionActorMessage.GetModel(BaseActorSpec.companyModel.r, self)
  //  expectMsg(ModelResult.ModelObject(BaseActorSpec.companyModel, List.empty))
  //
  //  //delete company model
  //  val newModels = models.filterNot(_.r == BaseActorSpec.companyModel.r)
  //  modelPersistentActor ! PersistentModelStore.UpdateModels(newModels)
  //  modelPersistentActor ! PersistentModelStoreFixture.GetState
  //  expectMsg(newModels)
  //
  //  modelRouterRef ! PersistenceUpdate(await = true)
  //  expectMsg(ModelOperation.ModelDeleted(BaseActorSpec.companyModel.r))
  //
  //  globals.modelRegistry ! PersistentModelStore.DeletedModels(List(BaseActorSpec.companyModel))
  //  val userObjx = Json.obj("name" -> "Cloud9ers", "employees" -> 23)
  //  val rx = R / 'companies / 456
  //  globals.collectionActor.tell(Create(ctx, rx, userObj), probe.ref)
  //  val resx = probe.expectMsgType[ModelResult]
  //  resx should be(ModelResult.ModelNotFound)
  //
  //  globals.collectionActor.tell(GetExistenceState(rx), probe.ref)
  //  val em1 = probe.expectMsgType[ModelResult]
  //  em1 should be(ModelResult.ModelNotFound)
  //
  //  globals.collectionActor.tell(Create(ctx, rx, Json.obj()), probe.ref)
  //  val em2 = probe.expectMsgType[ModelResult]
  //  em2 should be(ModelResult.ModelNotFound)
  //
  //  globals.collectionActor.tell(Update(ctx, rx, 0, null), probe.ref)
  //  val em3 = probe.expectMsgType[ModelResult]
  //  em3 should be(ModelResult.ModelNotFound)
  //
  //  globals.collectionActor.tell(Delete(ctx, rx), probe.ref)
  //  val em4 = probe.expectMsgType[ModelResult]
  //  em4 should be(ModelResult.ModelNotFound)
  //
  //  globals.collectionActor.tell(UnsupportedCommand(rx, "UNKNOWN CMD"), probe.ref)
  //  val em5 = probe.expectMsgType[ModelResult]
  //  em5 should be(ModelResult.ModelNotFound)
  //}
  //
  //it should "reply with InvalidCommand if an unknown command was sent" in {
  //  val r = R / 'users / 501
  //  val userObj = Json.obj("name" -> "Foo Bar", "age" -> 38)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  globals.collectionActor.tell(UnsupportedCommand(r, "UNKNOWN CMD"), probe.ref)
  //  val resx = probe.expectMsgType[InvalidCommand]
  //  resx.reason should include("UNKNOWN CMD")
  //}
  //
  //"Recovery" should "correctly retrieve the previously saved objects" in {
  //  val r = R / 'users / 23
  //  val actorName = Helpers.getBucketIDFromR(r)
  //  val collectionActor = system.actorOf(Props(new CollectionActor(globals)), actorName)
  //  val userObj = Json.obj("name" -> "Montaro", "age" -> 23)
  //  val probe = TestProbe()
  //  collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "name" shouldBe JsString("Montaro")
  //  collectionActor.tell(GetState(r), probe.ref)
  //  val state = probe.expectMsgType[State]
  //  state.obj \ "name" shouldBe JsString("Montaro")
  //  state.obj \ "age" shouldBe JsNumber(23)
  //
  //  probe watch collectionActor
  //  collectionActor ! PoisonPill
  //  probe.expectTerminated(collectionActor)
  //
  //  val collectionActorx = system.actorOf(Props(new CollectionActor(globals)), actorName)
  //  collectionActorx.tell(GetState(r), probe.ref)
  //  val statex = probe.expectMsgType[State]
  //  statex.obj \ "name" shouldBe JsString("Montaro")
  //  statex.obj \ "age" shouldBe JsNumber(23)
  //}
  //
  //it should "correctly retrieve the previously updated objects" in {
  //  val r = R / 'users / 122
  //  val actorName = Helpers.getBucketIDFromR(r)
  //  val collectionActor = system.actorOf(Props(new CollectionActor(globals)), actorName)
  //  val probe = TestProbe()
  //  collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
  //  collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsgType[UpdateResult]
  //  collectionActor.tell(GetState(r), probe.ref)
  //  val state = probe.expectMsgType[State]
  //  probe watch collectionActor
  //  collectionActor ! PoisonPill
  //  probe.expectTerminated(collectionActor)
  //  val collectionActorx = system.actorOf(Props(new CollectionActor(globals)), actorName)
  //  collectionActorx.tell(GetState(r), probe.ref)
  //  val statex = probe.expectMsgType[State]
  //  state shouldEqual statex
  //  (state.obj \ "name").as[String] shouldEqual "Amal"
  //
  //}
  //
  //"Create Command" should "create object sucessfully" in {
  //  val r = R / 'users / 124
  //  val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "name" shouldBe JsString("Hatem AlSum")
  //  res.body \ "age" shouldBe JsNumber(30)
  //  res.body \ "_rev" shouldBe JsNumber(1)
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  val state = probe.expectMsgType[State]
  //  state.obj \ "name" shouldBe JsString("Hatem AlSum")
  //  state.obj \ "age" shouldBe JsNumber(30)
  //  state.obj \ "_rev" shouldBe JsNumber(1)
  //}
  //
  //it should "reply with AlreadyExists is the object was already created" in {
  //  val r = R / 'users / 125
  //  val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  probe.expectMsgType[Result.CreateResult]
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  probe.expectMsgType[CommandError.AlreadyExists]
  //}
  //
  //it should "validate the object fields in creation against the Model specs" in {
  //  val r = R / 'cars / 124
  //  val userObj = Json.obj("model" -> "Lancer")
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val validationFailed = probe.expectMsgType[CommandError.ModelValidationFailed]
  //  validationFailed.reason.errors(0)._1 shouldBe JsPath() \ "model"
  //  validationFailed.reason.errors(0)._2(0) shouldBe ValidationError("validation.custom.failed")
  //}
  //
  //it should "reply with created object and contain default values if not submitted" in {
  //  val r = R / 'cars / 125
  //  val userObj = Json.obj("model" -> "Toyota Corolla")
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "production" shouldBe JsNumber(1980)
  //}
  //
  //it should "reply with created object and contain calculated values" in {
  //  val r = R / 'cars / 126
  //  val userObj = Json.obj("model" -> "Mitsubishi Lancer", "production" -> 2010)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "renewal" shouldBe JsNumber(2020)
  //}
  //
  //it should "reply with created object and contain CALCULATED values if dependant values not in model" in {
  //  val r = R / 'cars / 127
  //  val userObj = Json.obj("model" -> "Ford Focus")
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "renewal" shouldBe JsNumber(1990)
  //}
  //
  //it should "have the _r as a key in the persisted object" in {
  //  val r = R / 'users / 128
  //  val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  probe.expectMsgType[Result.CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  val state = probe.expectMsgType[State]
  //  state.obj \ "_r" shouldBe JsString(r.toString)
  //}
  //
  //it should "have the object revision as a part of the state" in {
  //  val r = R / 'users / 129
  //  val userObj = Json.obj("name" -> "Hatem AlSum", "age" -> 30)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  probe.expectMsgType[Result.CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  val state = probe.expectMsgType[State]
  //  state.obj \ "_rev" shouldBe JsNumber(1)
  //}
  //
  //it should "validate that model is not found" in {
  //  val r = R / "images" / 1
  //  val postObj = Json.obj("title" -> "First Post", "body" -> "First Body")
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, postObj), probe.ref)
  //  probe.expectMsg(ModelResult.ModelNotFound)
  //}

  it should "Create with Neseted SubModels" in {
    val r = R / 'authors / 1
    val Obj = Json.obj("name" -> "Hatem")
    val probe = TestProbe()
    globals.collectionActor.tell(Create(ctx, r, Obj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
    val cr = R / 'authors / 1 / 'posts / 1
    val postObj = Json.obj("title" -> "First Post", "body" -> "First Body")
    globals.collectionActor.tell(Create(ctx, cr, postObj), probe.ref)
    probe.expectMsgType[Result.CreateResult]
  }

  //it should "validate the object parent is alive" in {
  //  val cr = R / 'authors / 2 / 'posts / 2
  //  val postObj = Json.obj("title" -> "First Post", "body" -> "First Body")
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, cr, postObj), probe.ref)
  //  probe.expectMsgType[ParentNotFound]
  //}
  //
  //"Update" should "get ObjectNotFound for uncreated yet objects" in {
  //  val r = R / 'users / 123
  //  val body = UpdateBody(List.empty)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsg(CommandError.ObjectNotFound(r))
  //}
  //
  //it should "get InvalidCommand if the type of update operation isn't supported yet " in {
  //  val r = R / 'users / 123
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.AddToSet, "age", JsNumber(12))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  val em = probe.expectMsgType[CommandError]
  //  em.isInstanceOf[CommandError.ValidationFailed] shouldBe true
  //  val error = em.asInstanceOf[CommandError.ValidationFailed].reason
  //  error.errors(0)._2(0) shouldEqual ValidationError("error.not.supported")
  //}
  //
  //it should "update the state correctly when sending a valid Set Operation" in {
  //  val r = R / 'users / 111
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsgType[UpdateResult]
  //}
  //
  //it should "fail the data revision is lower than the field last touched revision and operation is Set operation" in {
  //  val r = R / 'users / 110
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsgType[UpdateResult]
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  val em = probe.expectMsgType[CommandError.ValidationFailed]
  //  val error = em.asInstanceOf[CommandError.ValidationFailed].reason
  //  error.errors(0)._2(0) shouldEqual ValidationError("error.revision.outdated")
  //}
  //
  //it should "ignore revision if the operation was AddNumber" in {
  //  val r = R / 'users / 1111
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "age", JsNumber(2))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsgType[UpdateResult]
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsgType[UpdateResult]
  //}
  //
  //it should "fail when sending an invalid value for AddNumber Operation" in {
  //  val r = R / 'users / 1112
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "age", JsString("a22"))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  val em = probe.expectMsgType[CommandError.ValidationFailed]
  //  val error = em.asInstanceOf[CommandError.ValidationFailed].reason
  //  error.errors(0)._2(0) shouldEqual ValidationError("error.value.should.be.number")
  //}
  //
  //it should "fail when sending AddNumber Operation on a field that hasn't Number type" in {
  //  val r = R / 'users / 11111
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "name", JsNumber(1))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  val em = probe.expectMsgType[CommandError.ValidationFailed]
  //  val error = em.asInstanceOf[CommandError.ValidationFailed].reason
  //  error.errors(0)._2(0) shouldBe ValidationError("error.non.numeric.field")
  //}
  //
  //it should "update the state correctly when sending a valid AddNumber Operation" in {
  //  val r = R / 'users / 111111
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.AddNumber, "age", JsNumber(1))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsgType[UpdateResult]
  //}
  //
  //it should "update the calculated Fields too" in {
  //  val r = R / 'cars / 199
  //  val userObj = Json.obj("model" -> "Mitsubishi Lancer", "production" -> 2010, "renewal" -> 2030)
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, userObj), probe.ref)
  //  val res = probe.expectMsgType[Result.CreateResult]
  //  res.body \ "renewal" shouldBe JsNumber(2020)
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "production", JsNumber(2012))))
  //  globals.collectionActor.tell(Update(ctx, r, 1l, body), probe.ref)
  //  probe.expectMsgType[UpdateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  val state = probe.expectMsgType[State]
  //  state.obj shouldEqual Json.obj("model" -> "Mitsubishi Lancer", "production" -> 2012, "renewal" -> 2020,
  //    "_r" -> "/cars/199/", "_rev" -> 2)
  //}
  //
  //it should "fail if the request revision is greater than the object revision" in {
  //  val r = R / 'users / 200
  //  val probe = TestProbe()
  //  globals.collectionActor.tell(Create(ctx, r, Json.obj("name" -> "amal elshihaby", "age" -> 27)), probe.ref)
  //  probe.expectMsgType[CreateResult]
  //  globals.collectionActor.tell(GetState(r), probe.ref)
  //  probe.expectMsgType[State]
  //  val body = UpdateBody(List(UpdateOp(UpdateCommand.Set, "name", JsString("Amal"))))
  //  globals.collectionActor.tell(Update(ctx, r, 10l, body), probe.ref)
  //  probe.expectMsg(CommandError.OutdatedRevision)
  //}

}

class CollectionTestReallyGlobals(override val config: ReallyConfig, override val actorSystem: ActorSystem) extends TestReallyGlobals(config, actorSystem) {
  override val materializerProps = Props.empty
}
