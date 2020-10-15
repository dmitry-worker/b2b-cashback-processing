import sbt.Keys.libraryDependencies
import sbt._

object Dependencies {

  lazy val scalaTest   = "org.scalatest"     %% "scalatest"   % "3.0.8"
  lazy val scalaMock   = "org.scalamock"     %% "scalamock"   % "4.1.0"
  lazy val enumeratum  = "com.beachape"      %% "enumeratum"  % "1.5.13"
  lazy val flyway      = "org.flywaydb"       % "flyway-core" % "5.0.7"
  lazy val leveldb     = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8"

  object Fuuid {
    lazy val fuuid       = "io.chrisdavenport" %% "fuuid"        % "0.2.0"
    lazy val fuuidoobie  = "io.chrisdavenport" %% "fuuid-doobie" % "0.2.0"
    lazy val all = fuuid :: fuuidoobie :: Nil
  }

  object Logging {
    lazy val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"  % "3.9.2"
    lazy val logbackClassic = "ch.qos.logback"             % "logback-classic" % "1.2.3"
    lazy val all = scalaLogging :: logbackClassic :: Nil
  }

  object Prometheus {
    lazy val client   = "io.prometheus" % "simpleclient"         % "0.6.0"
    lazy val hotspot  = "io.prometheus" % "simpleclient_hotspot" % "0.6.0"
    lazy val all = client :: hotspot :: Nil
  }

  object Http4s {
    val Version = "0.21.0-M5"
    lazy val http4sBlazeServer = "org.http4s"      %% "http4s-blaze-server" % Version
    lazy val http4sBlazeClient = "org.http4s"      %% "http4s-blaze-client" % Version
    lazy val http4sCirce       = "org.http4s"      %% "http4s-circe"        % Version
    lazy val http4sDsl         = "org.http4s"      %% "http4s-dsl"          % Version
    lazy val http4sMetrics     = "org.http4s"      %% "http4s-prometheus-metrics" % Version
    lazy val rho               = "org.http4s"      %% "rho-swagger"         % "0.20.0-M1"
    lazy val all = Seq(http4sBlazeClient, http4sBlazeServer, http4sCirce, http4sDsl, http4sMetrics, rho)
  }

  object Doobie {
    lazy val doobieCore = "org.tpolecat" %% "doobie-core"      % "0.7.0"
    lazy val doobieDbcp = "org.tpolecat" %% "doobie-hikari"    % "0.7.0"
    lazy val doobiePsql = "org.tpolecat" %% "doobie-postgres"  % "0.7.0"
    lazy val doobieSpec = "org.tpolecat" %% "doobie-specs2"    % "0.7.0" % "test"
    lazy val doobieTest = "org.tpolecat" %% "doobie-scalatest" % "0.7.0" % "test"
    lazy val postgis    = "net.postgis"   % "postgis-jdbc"     % "2.3.0"
    lazy val all = Seq(doobieCore, doobieDbcp, doobiePsql, doobieSpec, doobieTest, postgis)
  }

  object Guice {
    lazy val scalaGuice = "net.codingwell" %% "scala-guice" % "4.2.2"
    lazy val all = Seq(scalaGuice)
  }

  object TSec {
    lazy val Version = "0.2.0-M2"
    lazy val tsecCommon    = "io.github.jmcardon" %% "tsec-common"        % Version
    lazy val tsecSigns     = "io.github.jmcardon" %% "tsec-signatures"    % Version
    lazy val tsecPassword  = "io.github.jmcardon" %% "tsec-password"      % Version
    lazy val tsecHashJca   = "io.github.jmcardon" %% "tsec-hash-jca"      % Version
    lazy val tsecHashBcy   = "io.github.jmcardon" %% "tsec-hash-bouncy"   % Version
    lazy val tsecCipherJca = "io.github.jmcardon" %% "tsec-cipher-jca"    % Version
    lazy val tsecCipherBcy = "io.github.jmcardon" %% "tsec-cipher-bouncy" % Version
    lazy val tsecJwtMac    = "io.github.jmcardon" %% "tsec-jwt-mac"       % Version
    lazy val tsecJwtSig    = "io.github.jmcardon" %% "tsec-jwt-sig"       % Version
    lazy val all = Seq(tsecCommon, tsecSigns, tsecPassword, tsecHashBcy, tsecHashJca, tsecCipherBcy, tsecCipherJca, tsecJwtMac, tsecJwtSig)
  }

  object Circe {
    lazy val Version = "0.12.1"
    lazy val core    = "io.circe" %% "circe-core"            % Version
    lazy val generic = "io.circe" %% "circe-generic"         % Version
    lazy val extras  = "io.circe" %% "circe-generic-extras"  % Version
    lazy val parser  = "io.circe" %% "circe-parser"          % Version
    lazy val config  = "io.circe" %% "circe-config"          % "0.6.1"
    lazy val all = Seq(core, generic, extras, parser, config)
  }

  object RabbitMQ {
    lazy val Version        = "2.0.0-RC3"
    lazy val fs2Rabbit      = "dev.profunktor" %% "fs2-rabbit"       % Version
    lazy val fs2RabbitCirce = "dev.profunktor" %% "fs2-rabbit-circe" % Version
    lazy val all = Seq(fs2Rabbit, fs2RabbitCirce)
  }

  object Scheduler {
    lazy val scheduler = "eu.timepit" %% "fs2-cron-core" % "0.1.0"
    lazy val all = Seq(scheduler)
  }

  implicit class ProjectOps(proj: Project) {
    def withHttp:Project       = proj.settings(libraryDependencies ++= Http4s.all)
    def withRabbit:Project     = proj.settings(libraryDependencies ++= RabbitMQ.all)
    def withPsql:Project       = proj.settings(libraryDependencies ++= Doobie.all, libraryDependencies  += flyway)
    def withScheduler: Project = proj.settings(libraryDependencies ++= Scheduler.all)
  }

}
