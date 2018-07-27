package io.shaka.sbt

import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.plugins.JvmPlugin
import scala.sys.process.Process
import scala.util.Properties.envOrElse

object Npm extends AutoPlugin {

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

  def runNpm(exec: String, taskKey: String, commands: Seq[String], workingDir: String, logger: Logger): Unit = {
    val npmCommand = s"$exec ${commands.mkString(" ")}"
    logger.info(s"Running Task ${taskKey} command '$npmCommand' in $workingDir")
    val rc = Process(npmCommand, file(workingDir)).!
    if (rc != 0) {
      val errorMsg = s"$npmCommand returned non-zero return code: $rc"
      logger.error(errorMsg)
      sys.error(errorMsg)
    }
  }

  def runNpm(exec: String, taskKey: String, commands: String, workingDir: String, logger: Logger): Unit =
    runNpm(exec, taskKey, commands.split(" "), workingDir, logger)


  def runNpm(exec: String, commands: Seq[String], workingDir: String, logger: Logger): Unit = {
    runNpm(exec, "", commands, workingDir, logger)
  }

  def once[T](fn: () => T): () => T = {
    var hasRun = false
    var result: Any = null

    val retFn = () => {
      if (!hasRun) {
        hasRun = true
        result = fn.apply()
      }
      result.asInstanceOf[T]
    }
    retFn
  }

  // semi hack in making the compile and run commands run only once
  var runTest: () => Unit = null
  var runCompile: () => Unit = null

  override lazy val projectSettings = Seq(
    npmExec := envOrElse("NPM_PATH", "npm"), // NOTE THIS CAN BE A YARN PATH
    npmWorkingDir := ".",
    npmCompileCommands := envOrElse("NPM_COMPILE", ""),
    npmTestCommands := envOrElse("NPM_TEST", ""),
    npmCleanCommands := envOrElse("NPM_CLEAN", ""),
    npm := runNpm(npmExec.value, spaceDelimited("<arg>").parsed, npmWorkingDir.value, streams.value.log),
    (compile in Compile) := {
      // note one of the main reasons for dotEnv is that (compile in Compile)
      // and (test in Test) were not using their latest defined / overriden values
      val log = streams.value.log
      if(runCompile== null) {
        runNpm(npmExec.value,
          npmCompileCommands.key.label,
          npmCompileCommands.value,
          npmWorkingDir.value,
          streams.value.log)
        (compile in Compile).value
      }
      runCompile()
      (compile in Compile).value
    },
    (test in Test) := {
      val log = streams.value.log
      if(runTest == null) {
        runTest = once(() => runNpm(npmExec.value,
          npmTestCommands.key.label,
          npmTestCommands.value,
          npmWorkingDir.value,
          log))
      }
      runTest()
      (test in Test).value
    },
    clean := {
      runNpm(npmExec.value,
        npmCleanCommands.key.label,
        npmCleanCommands.value,
        npmWorkingDir.value,
        streams.value.log)
      clean.value
    }
  )

}
