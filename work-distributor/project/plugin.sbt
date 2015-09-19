resolvers ++= Seq("jgit-repo" at "http://download.eclipse.org/jgit/maven", Classpaths.sbtPluginReleases)

// Deploy fat JARs. Restart processes. (port of codahale/assembly-sbt)
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.12.0")

// An SBT plugin for dangerously fast development turnaround in Scala
addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.7.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.1.0")
