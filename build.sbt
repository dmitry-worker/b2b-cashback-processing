import Dependencies._
import NativePackagerHelper._
import com.typesafe.sbt.packager.docker.{Cmd, DockerChmodType}


val currentVersion = "0.4.2"


ThisBuild / scalaVersion     := "2.12.10"
ThisBuild / version          := currentVersion
ThisBuild / organization     := "com.noproject"
ThisBuild / organizationName := "NoProject"
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Ypartial-unification",
  "-Xfatal-warnings",
)
//ThisBuild / parallelExecution := false
//concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
resolvers += Resolver.sonatypeRepo("snapshots")

/////////////////////////////////////////////////////////////////////
////////////////////////////// TEST /////////////////////////////////

// SBT command: integration:test
val integrationTestFilter: String => Boolean = _ contains "Integration"
lazy val Integration = config("integration") extend (Test)

// SBT command: test
val commonTestFilter: String => Boolean = !integrationTestFilter.apply(_)


def testable(folder: String): Project = {
  Project(id = folder, base = file(folder))
    .configs(Integration)
    .settings(inConfig(Integration)(Defaults.testTasks): _*)
    .settings(
        testOptions in Test         := Seq(Tests.Filter(commonTestFilter))
      , testOptions in Integration  := Seq(Tests.Filter(integrationTestFilter))
      , parallelExecution in Integration := false
    )
}

def dockerImage(folder: String, ports: Seq[Int]):Project = {
  testable(folder)
    .settings(
      name      := folder
    , version   := currentVersion
    , version            in Docker := currentVersion
    , packageName        in Docker := folder
    , dockerExposedPorts := ports
    , dockerUpdateLatest := true
    , dockerBaseImage    := "adoptopenjdk/openjdk13:x86_64-alpine-jdk-13.0.1_9"
    , dockerChmodType    := DockerChmodType.UserGroupWriteExecute
    ).enablePlugins(JavaAppPackaging, AshScriptPlugin, DockerPlugin)
}

lazy val testServer = dockerImage("testserver", Seq(8888))
  .dependsOn(common % "compile->compile;test->test")
  .dependsOn(partnerAzigo % "compile->compile;test->test")
  .dependsOn(partnerButton % "compile->compile;test->test")
  .dependsOn(partnerCoupilia % "compile->compile;test->test")
  //.dependsOn(partnerMogl % "compile->compile;test->test")

////////////////////////////// TEST /////////////////////////////////
/////////////////////////////////////////////////////////////////////



/////////////////////////////////////////////////////////////////////
////////////////////////////// CORE /////////////////////////////////
lazy val common = testable("common").settings(
  libraryDependencies += scalaTest % Test
, libraryDependencies += scalaMock % Test
, libraryDependencies += enumeratum
, libraryDependencies += leveldb
, libraryDependencies += Prometheus.client
, libraryDependencies ++= Fuuid.all
, libraryDependencies ++= Logging.all
, libraryDependencies ++= Circe.all
, libraryDependencies ++= TSec.all
, libraryDependencies ++= Guice.all
//  , libraryDependencies ++= Akka.all
).withHttp.withPsql.withRabbit

lazy val merchApi = dockerImage("merch-api", Seq(8500, 9500)).settings(
  mappings in Universal ++= contentOf("merch-api/src/main/resources")
, mappings in Universal ++= directory("sql")
).withScheduler.dependsOn(common % "compile->compile;test->test")

lazy val processor = dockerImage("processor", Seq(8600, 9600))
  .withScheduler.dependsOn(common % "compile->compile;test->test")
  
lazy val statistics  = dockerImage("statistics", Seq(8700, 9700))
  .withScheduler.dependsOn(common % "compile->compile;test->test")
////////////////////////////// CORE /////////////////////////////////
/////////////////////////////////////////////////////////////////////


/////////////////////////////////////////////////////////////////////
//////////////////////////// PARTNERS ///////////////////////////////
lazy val partnerAzigo = dockerImage("partner-azigo", Seq(8501, 9501))
  .dependsOn(common % "compile->compile;test->test")

lazy val partnerButton = dockerImage("partner-button", Seq(8502, 9502))
  .dependsOn(common % "compile->compile;test->test")

lazy val partnerMogl = dockerImage("partner-mogl", Seq(8503, 9503))
  .dependsOn(common % "compile->compile;test->test")

lazy val partnerCoupilia = dockerImage("partner-coupilia", Seq(8504, 9504))
  .dependsOn(common % "compile->compile;test->test")

//////////////////////////// PARTNERS ///////////////////////////////
/////////////////////////////////////////////////////////////////////


lazy val root = (project in file("."))
  .aggregate(common, merchApi, processor, partnerAzigo, partnerButton, partnerMogl, partnerCoupilia, testServer)
  .settings(
    publish := {}
  )
