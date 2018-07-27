package io.shaka.sbt

import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.plugins.JvmPlugin
import scala.sys.process.Process

object Npm extends AutoPlugin{

  override def trigger = allRequirements
  override def requires: Plugins = JvmPlugin

  object autoImport {
    val npmExec = settingKey[String](
      "sbt-npm location of npm executable - defaults to npm and assume it is on the path")
    val npmWorkingDir = settingKey[String](
      "sbt-npm working directory for running npm commands - '.'")
    val npm = inputKey[Unit]("Run an npm command")
    val npmCompileCommands = settingKey[String]("sbt-npm npm commands to run during compile phase")
    val npmTestCommands = settingKey[String]("sbt-npm npm commands to run during test phase")
    val npmCleanCommands = settingKey[String]("sbt-npm npm commands to run during clean phase")
//    val npmPackageCommands = settingKey[String]("sbt-npm npm commands to run during package phase")
  }

  import autoImport._

  def runNpm(exec: String, commands: Seq[String], workingDir: String, logger: Logger):Unit = {
    val npmCommand = s"$exec ${commands.mkString(" ")}"
    logger.info(s"Running '$npmCommand' in $workingDir")
    val rc = Process(npmCommand, file(workingDir)).!
    if (rc != 0) {
      val errorMsg = s"$npmCommand returned non-zero return code: $rc"
      logger.error(errorMsg)
      sys.error(errorMsg)
    }
  }

  def runNpm(exec: String, commands: String, workingDir: String, logger: Logger): Unit = runNpm(exec, commands.split(" "), workingDir, logger)

  def runCompile(): Unit = {
    println(s"COMPILE Running '${npmExec.value}' ${npmCompileCommands.value}")
    runNpm(npmExec.value, npmCompileCommands.value, npmWorkingDir.value, streams.value.log)
  }

  override lazy val projectSettings = Seq(
    npmExec := "npm",
    npmWorkingDir := ".",
    npmCompileCommands :="",
    npmTestCommands :="",
    npmCleanCommands :="",
//    npmPackageCommands :="",
    npm := runNpm(npmExec.value, spaceDelimited("<arg>").parsed, npmWorkingDir.value, streams.value.log) ,
    (compile in Compile) := {
      runCompile()
      (compile in Compile).value
    },
    (test in Test) := {
      println(s"TEST Running '${npmExec.value}' ${npmTestCommands.value}")
      runNpm(npmExec.value, npmTestCommands.value, npmWorkingDir.value, streams.value.log)
      (test in Test).value
    },
    clean := {
      runNpm(npmExec.value, npmCleanCommands.value, npmWorkingDir.value, streams.value.log)
      clean.value
    }
  )

}
