package tests

import java.net.URI

import ch.epfl.scala.{bsp4j, bsp => bsp4s}
import com.google.gson.{Gson, GsonBuilder, JsonElement}
import io.circe.syntax._
import io.circe.parser.decode
import org.scalatest.FunSuite

import scala.collection.JavaConverters._

class SerializationSuite extends FunSuite {

  val gson: Gson = new GsonBuilder().setPrettyPrinting().create()

  test("TaskFinishParams") {
    val buildTarget = new URI("anUri")

    val target = bsp4s.BuildTargetIdentifier(bsp4s.Uri(buildTarget))
    val id = bsp4s.TaskId("task", Some(List("p1","p2","p3")))
    val report = bsp4s.CompileReport(target, Some("origin"), 13, 12, Some(77))
    val bsp4sValue = bsp4s.TaskFinishParams(
      id, Some(12345), Some("message"), bsp4s.StatusCode.Ok,
      Some(bsp4s.TaskDataKind.CompileReport), Some(report.asJson))

    val bsp4sJson = bsp4sValue.asJson.toString()
    val bsp4jValue = gson.fromJson(bsp4sJson, classOf[bsp4j.TaskFinishParams])
    val bsp4jJson = gson.toJson(bsp4jValue)
    val bsp4sValueDecoded = decode[bsp4s.TaskFinishParams](bsp4jJson).right.get

    val bsp4jValueData = gson.fromJson(bsp4jValue.getData.asInstanceOf[JsonElement], classOf[bsp4j.CompileReport])

    assert(bsp4jValue.getTaskId.getId == bsp4sValue.taskId.id)
    assert(bsp4jValue.getTaskId.getParents.asScala == bsp4sValue.taskId.parents.get)
    assert(bsp4jValue.getEventTime == bsp4sValue.eventTime.get)
    assert(bsp4jValue.getDataKind == bsp4sValue.dataKind.get)
    assert(bsp4jValue.getStatus.getValue == bsp4sValue.status.code)

    assert(bsp4jValueData.getOriginId == report.originId.get)
    assert(bsp4jValueData.getTarget.getUri == report.target.uri.value)
    assert(bsp4jValueData.getErrors == report.errors)
    assert(bsp4jValueData.getWarnings == report.warnings)
    assert(bsp4jValueData.getTime == report.time.get)

    assert(bsp4sValueDecoded == bsp4sValue)
  }

  test("PublishDiagnosticsParams") {
    val buildTarget = new URI("build.target")
    val textDocument = new URI("text.document")

    val range1 = bsp4s.Range(bsp4s.Position(1,1), bsp4s.Position(1,12))
    val diagnostic1 = bsp4s.Diagnostic(range1, Some(bsp4s.DiagnosticSeverity.Error), None, None, "message", None)

    val bsp4sValue = bsp4s.PublishDiagnosticsParams(
      bsp4s.TextDocumentIdentifier(bsp4s.Uri(textDocument)),
      bsp4s.BuildTargetIdentifier(bsp4s.Uri(buildTarget)),
      Some("origin"),
      List(diagnostic1),
      reset = false
    )

    val bsp4sJson = bsp4sValue.asJson.toString()
    val bsp4jValue = gson.fromJson(bsp4sJson, classOf[bsp4j.PublishDiagnosticsParams])
    val bsp4jJson = gson.toJson(bsp4jValue)
    val bsp4sValueDecoded = decode[bsp4s.PublishDiagnosticsParams](bsp4jJson).right.get

    assert(bsp4jValue.getBuildTarget.getUri == bsp4sValue.buildTarget.uri.value)
    assert(bsp4jValue.getOriginId == bsp4sValue.originId.get)
    assert(bsp4jValue.getReset == bsp4sValue.reset)
    assert(bsp4jValue.getTextDocument.getUri == bsp4sValue.textDocument.uri.value)
    assert(bsp4jValue.getDiagnostics.get(0).getMessage == bsp4sValue.diagnostics.head.message)
    assert(bsp4jValue.getDiagnostics.get(0).getSeverity.getValue == bsp4sValue.diagnostics.head.severity.get.id)

    assert(bsp4sValueDecoded == bsp4sValue)
  }

  test("BuildTargetCapabilities - backward compatible canDebug") {
    val legacyJson =
      """
        |{
        |  "canCompile": true,
        |  "canTest": true,
        |  "canRun": true
        |}""".stripMargin

    val bsp4jValue = gson.fromJson(legacyJson, classOf[bsp4j.BuildTargetCapabilities])
    val bsp4sValue = decode[bsp4s.BuildTargetCapabilities](legacyJson).right.get

    assert(bsp4jValue.getCanCompile == bsp4sValue.canCompile)
    assert(bsp4jValue.getCanTest == bsp4sValue.canTest)
    assert(bsp4jValue.getCanRun == bsp4sValue.canRun)
    assert(bsp4jValue.getCanDebug == bsp4sValue.canDebug)

    assert(bsp4jValue.getCanDebug == false)
    assert(bsp4sValue.canDebug == false)
  }
}
