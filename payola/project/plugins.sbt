logLevel := Level.Warn

resolvers ++= Seq(
    DefaultMavenRepository,
    // "SBT IDEA Repository" at "https://mpeltonen.github.com/maven/",
    "Typesafe Repository" at "https://repo.typesafe.com/typesafe/ivy-releases/",
    "SBT plugins Repository" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/",
    "doc Repository" at "https://repo.typesafe.com/typesafe/releases/",
    "Maven central Repository" at "https://repo1.maven.org/maven2/",
    // Resolver.url("Play", url("https://download.playframework.org/ivy-releases/"))(Resolver.ivyStylePatterns),
	  Resolver.url("sbt-plugin-releases on bintray", new URL("https://dl.bintray.com/sbt/sbt-plugin-releases/"))(Resolver.ivyStylePatterns)
)

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.1.0")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "3.0.0")

// https://github.com/jrudolph/sbt-dependency-graph/
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-start-script" % "0.10.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.10")
