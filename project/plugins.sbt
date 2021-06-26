// addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.14")

libraryDependencies += "com.h2database" % "h2" % "1.4.196"
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.18"

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.github.tototoshi" % "sbt-slick-codegen" % "1.4.0")
addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % "7.4.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.23")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.10.0")
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.13")
addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.0")
addSbtPlugin(
  "com.thoughtworks.deeplearning" % "sbt-ammonite-classpath" % "2.0.0"
)
// addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.8.2")
