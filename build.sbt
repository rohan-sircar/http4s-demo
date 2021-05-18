val Http4sVersion = "0.21.16"
val CirceVersion = "0.13.0"
val MunitVersion = "0.7.20"
val LogbackVersion = "1.2.3"
val MunitCatsEffectVersion = "0.13.0"
val FlywayVersion = "7.5.3"
val MonixVerison = "3.3.0"
// val MonixBioVersion = "1.1.0"
val MonixBioVersion = "0a2ad29275"
val SttpVersion = "3.3.1"
val OdinVersion = "0.11.0"
val TestContainersVersion = "0.39.3"
val PureconfigVersion = "0.14.0"
val RefinedVersion = "0.9.19"
scalaVersion in ThisBuild := "2.13.4"

resolvers in ThisBuild += "jitpack" at "https://jitpack.io"

import com.github.tototoshi.sbt.slick.CodegenPlugin.autoImport.{
  slickCodegenDatabasePassword,
  slickCodegenDatabaseUrl,
  slickCodegenJdbcDriver
}

import slick.codegen.SourceCodeGenerator
import slick.{model => m}

lazy val codegenDbHost =
  sys.env.getOrElse("HTTP4S_DEMO_CODEGEN_DB_HOST", "localhost")
lazy val codegenDbPort =
  sys.env.getOrElse("HTTP4S_DEMO_CODEGEN_DB_PORT", "5432")
lazy val codegenDbName =
  sys.env.getOrElse("HTTP4S_DEMO_CODEGEN_DB_NAME", "test_db")

lazy val databaseUrl =
  s"jdbc:postgresql://$codegenDbHost:$codegenDbPort/$codegenDbName"

lazy val databaseUser =
  sys.env.getOrElse("HTTP4S_DEMO_CODEGEN_DB_USER", "test_user")
lazy val databasePassword =
  sys.env.getOrElse("HTTP4S_DEMO_CODEGEN_DB_PASSWORD", "password")

// alpine java docker image for smaller size - "azul/zulu-openjdk-alpine:11-jre-headless"
lazy val dockerJavaImage =
  sys.env.getOrElse(
    "HTTP4S_DEMO_DOCKER_JAVA_IMAGE",
    "openjdk:11-jre-slim-buster"
  )

lazy val flyway = (project in file("modules/flyway"))
  .enablePlugins(FlywayPlugin)
  .settings(
    libraryDependencies += "org.flywaydb" % "flyway-core" % FlywayVersion,
    flywayLocations := Seq("classpath:db/migration/default"),
    flywayUrl := databaseUrl,
    flywayUser := databaseUser,
    flywayPassword := databasePassword,
    flywayBaselineOnMigrate := true
  )

lazy val testCommon = (project in file("modules/test-common"))
  .settings(
    libraryDependencies ++= Seq(
      "com.github.monix" % "monix-bio" % "0a2ad29275",
      "com.github.valskalla" %% "odin-monix" % OdinVersion,
      "de.lolhens" %% "munit-tagless-final" % "0.0.1",
      "be.venneborg" %% "slick-refined" % "0.5.0",
      "com.beachape" %% "enumeratum-slick" % "1.6.0",
      "com.github.tminglei" %% "slick-pg" % "0.19.6",
      "com.github.tminglei" %% "slick-pg_circe-json" % "0.19.6"
    )
  )

lazy val root = (project in file("."))
  .enablePlugins(
    CodegenPlugin,
    DockerPlugin,
    JavaAppPackaging,
    AshScriptPlugin,
    BuildInfoPlugin,
    GitBranchPrompt
  )
  .configs(IntegrationTest)
  .settings(
    organization := "wow.doge",
    name := "http4s-demo",
    version in Docker := sys.env
      .get("HTTP4S_DEMO_DOCKER_PUBLISH_TAG")
      .map(s => if (s.startsWith("v")) s.tail else s)
      .getOrElse(version.value),
    dockerBaseImage := dockerJavaImage,
    dockerExposedPorts := Seq(8081),
    dockerUsername := Some("rohansircar"),
    Defaults.itSettings,
    inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)),
    buildInfoOptions ++= Seq(BuildInfoOption.ToJson, BuildInfoOption.BuildTime),
    buildInfoPackage := "wow.doge.http4sdemo",
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-language:existentials",
      "-language:experimental.macros",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xlint",
      "-Ywarn-numeric-widen",
      "-Ymacro-annotations",
      //silence warnings for by-name implicits
      "-Wconf:cat=lint-byname-implicit:s",
      //give errors on non exhaustive matches
      "-Wconf:msg=match may not be exhaustive:e",
      // """-Wconf:site=wow\.doge\.http4sdemo\.slickcodegen\Tables\$:i""",
      "-Wconf:msg=early initializers are deprecated:i",
      """-Wconf:site=wow\.doge\.http4sdemo\.slickcodegen\..*:i""",
      // """-Wconf:src=target/src_managed/Tables.scala:s""",
      "-explaintypes" // Explain type errors in more detail.
    ),
    scalacOptions ++= {
      if (insideCI.value) Seq("-Xfatal-warnings")
      else Seq.empty
    },
    javacOptions ++= Seq("-source", "11", "-target", "11"),
    //format: off
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server"  % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client"  % Http4sVersion,
      "org.http4s"      %% "http4s-dropwizard-metrics" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"         % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"           % Http4sVersion,
      "io.circe"        %% "circe-generic"        % CirceVersion,
      "org.scalameta"   %% "munit"                % MunitVersion           % "it,test",
      "org.typelevel"   %% "munit-cats-effect-2"  % MunitCatsEffectVersion % "it,test",
      "ch.qos.logback"  %  "logback-classic"      % LogbackVersion,
      "org.scalameta"   %% "svm-subs"             % "20.2.0",
      "co.fs2"          %% "fs2-reactive-streams" % "2.5.0",
    ),
    //format: on
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % MonixVerison,
      // "io.monix" %% "monix-bio" % "1.1.0",
      "com.github.monix" % "monix-bio" % MonixBioVersion,
      "com.softwaremill.sttp.client3" %% "core" % SttpVersion,
      "com.softwaremill.sttp.client3" %% "monix" % SttpVersion,
      "com.softwaremill.sttp.client3" %% "circe" % SttpVersion,
      "com.softwaremill.sttp.client3" %% "httpclient-backend-monix" % SttpVersion,
      "com.softwaremill.quicklens" %% "quicklens" % "1.6.1",
      "com.softwaremill.common" %% "tagging" % "2.2.1",
      "com.softwaremill.macwire" %% "macros" % "2.3.6" % "provided",
      "com.github.valskalla" %% "odin-monix" % OdinVersion,
      "com.github.valskalla" %% "odin-slf4j" % OdinVersion,
      "com.github.valskalla" %% "odin-json" % OdinVersion,
      "com.github.valskalla" %% "odin-extras" % OdinVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.lihaoyi" %% "os-lib" % "0.7.1",
      "com.beachape" %% "enumeratum" % "1.6.1",
      "com.beachape" %% "enumeratum-slick" % "1.6.0",
      "com.chuusai" %% "shapeless" % "2.3.3",
      "com.lihaoyi" %% "sourcecode" % "0.2.1",
      "eu.timepit" %% "refined" % RefinedVersion,
      "eu.timepit" %% "refined-pureconfig" % RefinedVersion,
      "com.zaxxer" % "HikariCP" % "3.4.2",
      "com.typesafe.slick" %% "slick" % "3.3.3",
      "com.typesafe.slick" %% "slick-hikaricp" % "3.3.3",
      "com.h2database" % "h2" % "1.4.199",
      "org.postgresql" % "postgresql" % "42.2.18",
      "com.github.pureconfig" %% "pureconfig" % PureconfigVersion,
      "com.github.pureconfig" %% "pureconfig-enumeratum" % PureconfigVersion,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % PureconfigVersion,
      "io.scalaland" %% "chimney" % "0.6.1",
      "io.scalaland" %% "chimney-cats" % "0.6.1",
      "com.rms.miu" %% "slick-cats" % "0.10.4",
      "com.kubukoz" %% "slick-effect" % "0.3.0",
      "io.circe" %% "circe-fs2" % CirceVersion,
      "io.circe" %% "circe-refined" % CirceVersion,
      "io.estatico" %% "newtype" % "0.4.4",
      "be.venneborg" %% "slick-refined" % "0.5.0",
      "com.github.tminglei" %% "slick-pg" % "0.19.6",
      "com.github.tminglei" %% "slick-pg_circe-json" % "0.19.6",
      // "org.scalameta" %% "munit" % "0.7.23" % "it,test",
      "de.lolhens" %% "munit-tagless-final" % "0.0.1" % "it,test",
      "org.scalameta" %% "munit-scalacheck" % "0.7.23" % "it,test",
      "org.scalacheck" %% "scalacheck" % "1.15.3" % "it,test",
      "com.dimafeng" %% "testcontainers-scala-munit" % TestContainersVersion % IntegrationTest,
      "com.dimafeng" %% "testcontainers-scala-postgresql" % TestContainersVersion % IntegrationTest
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      sbtVersion,
      libraryDependencies,
      javacOptions,
      dockerBaseImage
    )
  )
  .settings(
    slickCodegenDatabaseUrl := databaseUrl,
    slickCodegenDatabaseUser := databaseUser,
    slickCodegenDatabasePassword := databasePassword,
    slickCodegenDriver := slick.jdbc.PostgresProfile,
    // slickCodegenDriver := RefinedPgProfile,
    slickCodegenJdbcDriver := "org.postgresql.Driver",
    slickCodegenOutputPackage := "wow.doge.http4sdemo.slickcodegen",
    slickCodegenExcludedTables := Seq("flyway_schema_history"),
    slickCodegenCodeGenerator := { (model: m.Model) =>
      new SourceCodeGenerator(model) {
        // override def code = """
        // """.stripMargin + "\n" + super.code

        override def Table = new Table(_) {
          // override def EntityType = new EntityType {
          //   override def caseClassFinal = true
          // }
          override def Column = new Column(_) {
            override def rawType = model.tpe match {
              case "java.sql.Date"      => "LocalDate"
              case "java.sql.Time"      => "LocalTime"
              case "java.sql.Timestamp" => "LocalDateTime"
              case _ =>
                model.name match {
                  //books fields
                  case "book_id"    => "BookId"
                  case "book_name"  => "BookName"
                  case "book_title" => "BookTitle"
                  case "book_isbn"  => "BookIsbn"
                  //authors fields
                  case "author_id"   => "AuthorId"
                  case "author_name" => "AuthorName"
                  //others
                  case "color"                  => "Color"
                  case s if s.endsWith("_json") => "Json"
                  case _                        => super.rawType
                }
            }
          }
        }
        override def packageCode(
            profile: String,
            pkg: String,
            container: String,
            parentType: Option[String]
        ): String = {
          //format: off
          s"""
          |package ${pkg}
          |// AUTO-GENERATED Slick data model
          |/** Stand-alone Slick data model for immediate use */
          |object ${container} extends ${container} {
          |  val profile = wow.doge.http4sdemo.profile.ExtendedPgProfile
          |}
          |
          |/** Slick data model trait for extension, choice of backend or usage in the cake pattern. 
          |  * (Make sure to initialize this late.) */
          |trait ${container}${parentType.map(t => s" extends $t").getOrElse("")} {
          |  val profile: wow.doge.http4sdemo.profile.ExtendedPgProfile
          |  import profile.api._
          |  import wow.doge.http4sdemo.profile.ExtendedPgProfile.mapping._
          |  import wow.doge.http4sdemo.models.Refinements._
          |  import wow.doge.http4sdemo.models.common._
          |  import io.circe.Json
          |  import java.time._
          |  ${indent(code)}
          |}
      """.stripMargin
          //format: on
        }
      }
    },
    sourceGenerators in Compile += slickCodegen.taskValue
  )
  .dependsOn(flyway, testCommon)

ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.4.3"
inThisBuild(
  List(
    scalaVersion := scalaVersion.value, // 2.11.12, or 2.13.3
    semanticdbEnabled := true, // enable SemanticDB
    semanticdbVersion := "4.4.2", // use Scalafix compatible version
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    dynverSeparator := "-"
  )
)

addCommandAlias("lint-check", "scalafmtCheckAll; scalafixAll --check")
addCommandAlias("lint-run", "scalafmtAll; scalafixAll")

wartremoverErrors in (Compile, compile) ++=
  Warts.allBut(
    Wart.Any,
    Wart.NonUnitStatements,
    Wart.StringPlusAny,
    Wart.Overloading,
    Wart.PublicInference,
    Wart.Nothing,
    Wart.Var,
    Wart.DefaultArguments,
    Wart.OptionPartial,
    // Wart.MutableDataStructures,
    Wart.ImplicitConversion,
    Wart.ImplicitParameter,
    Wart.ToString,
    Wart.Recursion,
    Wart.While,
    Wart.ExplicitImplicitTypes,
    Wart.ListUnapply
  )
wartremoverExcluded += (sourceManaged in Compile).value
