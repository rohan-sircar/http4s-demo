// addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.14")
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.10")

// https://github.com/tototoshi/sbt-slick-codegen
libraryDependencies += "com.h2database" % "h2" % "1.4.196"
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.18"

addSbtPlugin("com.github.tototoshi" % "sbt-slick-codegen" % "1.4.0")
// Database migration
// https://github.com/flyway/flyway-sbt
addSbtPlugin("io.github.davidmweber" % "flyway-sbt" % "7.4.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.23")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.0")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
