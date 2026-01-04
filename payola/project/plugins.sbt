logLevel := Level.Warn

resolvers ++= Seq(
    DefaultMavenRepository,
    // "SBT IDEA Repository" at "https://mpeltonen.github.com/maven/",
    "Typesafe Repository" at "https://repo.typesafe.com/typesafe/ivy-releases/",
    "SBT plugins Repository" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/",
    "doc Repository" at "https://repo.typesafe.com/typesafe/releases/",
    "Maven central Repository" at "https://repo1.maven.org/maven2/",
    // Resolver.url("Play", url("https://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns),
	  Resolver.url("sbt-plugin-releases on bintray", new URL("https://dl.bintray.com/sbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns),
    "central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"
)

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.1.0")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "5.2.4")

/** Needed for Play 2.6 which depends on Twirl that claims scala-xml 1.0.6
 * is required, but this version is not compatible with Scala 2.12+.
 * Backwards compatibility for scala-xml 1.0.6 should sufficient in 2.3.0.
 * 
 * [warn] found version conflict(s) in library dependencies; some are suspected to be binary incompatible:
 * [warn]  * org.scala-lang.modules:scala-xml_2.12:2.3.0 (early-semver) is selected over 1.0.6
 * [warn]      +- org.scala-lang:scala-compiler:2.12.20              (depends on 2.3.0)
 * [warn]      +- com.typesafe.play:twirl-api_2.12:1.3.16            (depends on 1.0.6)
*/

// Source - https://stackoverflow.com/questions/74335368/scala-sbt-version-dependency-binary-compatibility-error-scala-xml
// Posted by joesan, modified by community.
// Retrieved 2026-01-03, License - CC BY-SA 4.0

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "2.3.0"
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.11.4")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.25")

addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "2.0.17")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.3.0")

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.18" % Test