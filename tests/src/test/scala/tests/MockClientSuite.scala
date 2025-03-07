package tests

import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.Executors
import ch.epfl.scala.bsp.testkit.client.TestClient
import ch.epfl.scala.bsp.testkit.mock.MockServer
import ch.epfl.scala.bsp.testkit.mock.MockServer.{LocalMockServer, clientLauncher}
import ch.epfl.scala.bsp4j._
import com.google.common.collect.Lists
import org.scalatest.FunSuite

import java.net.URI
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

class MockClientSuite extends FunSuite {

  private val testDirectory = Files.createTempDirectory("bsp.MockClientSuite")
  private val initializeBuildParams =
    new InitializeBuildParams(
      "Mock-Client",
      "0.0",
      "2.0",
      testDirectory.toUri.toString,
      new BuildClientCapabilities(Collections.singletonList("scala"))
    )

  val baseUri: URI = testDirectory.toFile.getCanonicalFile.toURI
  private implicit val executionContext: ExecutionContextExecutor =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  val targetId1 = new BuildTargetIdentifier(baseUri.resolve("target1").toString)
  val targetId2 = new BuildTargetIdentifier(baseUri.resolve("target2").toString)
  val targetId3 = new BuildTargetIdentifier(baseUri.resolve("target3").toString)

  val target2 = new BuildTarget(
    targetId2,
    List(BuildTargetTag.TEST).asJava,
    Collections.emptyList(),
    Collections.emptyList(),
    new BuildTargetCapabilities(true, true, false, false)
  )
  val target3 = new BuildTarget(
    targetId3,
    List(BuildTargetTag.APPLICATION).asJava,
    Collections.emptyList(),
    Collections.emptyList(),
    new BuildTargetCapabilities(true, false, true, false)
  )

  private val client = TestClient(
    () => {
      val LocalMockServer(running, in, out) = MockServer.startMockServer(testDirectory.toFile)
      (out, in, () => running.cancel(true))
    },
    initializeBuildParams
  )

  test("Initialize connection followed by its shutdown") {
    client.testInitializeAndShutdown()
  }

  test("Initial imports") {
    client.testResolveProject()
  }

  test("Test server capabilities") {
    client.testTargetCapabilities()
  }

  test("Running batch tests") {
    client.wrapTest(
      session => {
        client
          .resolveProject(session)
          .map(_ => client.targetsCompileSuccessfully(session))
          .flatMap(_ => client.cleanCacheSuccessfully(session))
      }
    )
  }

  test("Test Compile of all targets") {
    client.testTargetsCompileSuccessfully(true)
  }

  test("Clean cache") {
    client.testCleanCacheSuccessfully()
  }

  test("Run Targets") {
    client.testTargetsRunSuccessfully(
      Lists.newArrayList(
        target3
      )
    )
  }

  test("Run Tests") {
    client.testTargetsTestSuccessfully(
      Lists.newArrayList(
        target2
      )
    )
  }

  test("Run  javacOptions") {
    val javacOptionsItems = List(
      new JavacOptionsItem(targetId1, Collections.emptyList(), List("guava").asJava, "out"),
      new JavacOptionsItem(targetId2, Collections.emptyList(), List("guava").asJava, "out"),
      new JavacOptionsItem(targetId3, Collections.emptyList(), List("guava").asJava, "out")
    ).asJava

    client.testJavacOptions(
      new JavacOptionsParams(Collections.emptyList()),
      new JavacOptionsResult(javacOptionsItems)
    )
  }

  test("Run  scalacOptions") {
    val scalacOptionsItems = List(
      new ScalacOptionsItem(
        targetId1,
        Collections.emptyList(),
        List("scala-library").asJava,
        "out"
      ),
      new ScalacOptionsItem(
        targetId2,
        Collections.emptyList(),
        List("scala-library").asJava,
        "out"
      ),
      new ScalacOptionsItem(
        targetId3,
        Collections.emptyList(),
        List("scala-library").asJava,
        "out"
      )
    ).asJava

    client.testScalacOptions(
      new ScalacOptionsParams(Collections.emptyList()),
      new ScalacOptionsResult(scalacOptionsItems)
    )
  }

  test("Run Scala Test Classes") {
    val classes1 = List("class1").asJava
    val classes2 = List("class2").asJava
    val testClassesItems = List(
      new ScalaTestClassesItem(targetId1, classes1),
      new ScalaTestClassesItem(targetId2, classes2)
    ).asJava
    val result = new ScalaTestClassesResult(testClassesItems)
    client.testScalaTestClasses(
      new ScalaTestClassesParams(Collections.emptyList()),
      result
    )
  }

  test("Scala Test Classes with less items should fail") {
    val classes1 = List("class1").asJava
    val testClassesItems = List(new ScalaTestClassesItem(targetId1, classes1)).asJava
    val result = new ScalaTestClassesResult(testClassesItems)
    Try(
      client.testScalaTestClasses(
        new ScalaTestClassesParams(Collections.emptyList()),
        result
      )
    ) match {
      case Failure(_) =>
      case Success(_) => fail("Test Classes should expect all item classes to be defined!")
    }
  }

  test("Run Scala Main Classes") {
    val classes1 = List(
      new ScalaMainClass("class1", List("arg1", "arg2").asJava, List("-deprecated").asJava)
    ).asJava
    val classes2 = List(
      new ScalaMainClass("class2", List("arg1", "arg2").asJava, List("-deprecated").asJava)
    ).asJava
    val mainClassesItems = List(
      new ScalaMainClassesItem(targetId1, classes1),
      new ScalaMainClassesItem(targetId1, classes2)
    ).asJava
    val result = new ScalaMainClassesResult(mainClassesItems)
    client.testScalaMainClasses(
      new ScalaMainClassesParams(Collections.emptyList()),
      result
    )
  }

  test("Scala Main Classes with less items should fail") {
    val classes1 = List(
      new ScalaMainClass("class1", List("arg1", "arg2").asJava, List("-deprecated").asJava)
    ).asJava
    val mainClassesItems = List(new ScalaMainClassesItem(targetId1, classes1)).asJava
    val result = new ScalaMainClassesResult(mainClassesItems)
    Try(
      client.testScalaMainClasses(
        new ScalaMainClassesParams(Collections.emptyList()),
        result
      )
    ) match {
      case Failure(_) =>
      case Success(_) => fail("Test Classes should expect all item classes to be defined!")
    }
  }


  private lazy val environmentItem = {
    val classpath = List("scala-library").asJava
    val jvmOptions = List("-Xms256m").asJava
    val environmentVariables = Map("A" -> "a").asJava
    val workdir = "/tmp"
    val item1 = new JvmEnvironmentItem(
      targetId1,
      classpath,
      jvmOptions,
      workdir,
      environmentVariables
    )
    List(item1).asJava
  }

  test("Jvm Run Environment"){
    client.testJvmRunEnvironment(new JvmRunEnvironmentParams(Collections.emptyList()), new JvmRunEnvironmentResult(environmentItem))
  }

  test("Jvm Test Environment"){
    client.testJvmTestEnvironment(new JvmTestEnvironmentParams(Collections.emptyList()), new JvmTestEnvironmentResult(environmentItem))
  }

}
