import BuildKeys._
import Boilerplate._

//import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}
//import sbtcrossproject.CrossProject

// ---------------------------------------------------------------------------
// Commands

addCommandAlias("release", ";+clean ;ci-release ;unidoc ;microsite/publishMicrosite")
addCommandAlias("ci", ";project root ;reload ;+clean ;+test:compile ;+test ;+package ;unidoc ;site/makeMicrosite")

// ---------------------------------------------------------------------------
// Dependencies

/** Standard FP library for Scala:
  * [[https://typelevel.org/cats/]]
  */
val CatsVersion = "2.1.0"

/** FP library for describing side-effects:
  * [[https://typelevel.org/cats-effect/]]
  */
val CatsEffectVersion = "2.0.0"

/** Library for unit-testing:
  * [[https://github.com/monix/minitest/]]
  */
val MinitestVersion = "2.7.0"

/** Library for property-based testing:
  * [[https://www.scalacheck.org/]]
  */
val ScalaCheckVersion = "1.14.1"

/** Compiler plugin for working with partially applied types:
  * [[https://github.com/typelevel/kind-projector]]
  */
val KindProjectorVersion = "0.11.0"

/** Compiler plugin for fixing "for comprehensions" to do desugaring w/o `withFilter`:
  * [[https://github.com/typelevel/kind-projector]]
  */
val BetterMonadicForVersion = "0.3.1"

/** Compiler plugin for silencing compiler warnings:
  * [[https://github.com/ghik/silencer]]
  */
val SilencerVersion = "1.4.4"

/**
  * Defines common plugins between all projects.
  */
def defaultPlugins: Project ⇒ Project = pr => {
  val withCoverage = sys.env.getOrElse("SBT_PROFILE", "") match {
    case "coverage" => pr
    case _ => pr.disablePlugins(scoverage.ScoverageSbtPlugin)
  }
  withCoverage
    .enablePlugins(AutomateHeaderPlugin)
    .enablePlugins(GitBranchPrompt)
}

lazy val sharedSettings = Seq(
  projectTitle := "SBT Openapi codegen plugin",
  projectWebsiteRootURL := "https://mercurievv.github.io/",
  projectWebsiteBasePath := "/sbt-openapi-generator-plugin/",
  githubOwnerID := "MercurieVV",
  githubRelativeRepositoryID := "sbt-openapi-generator-plugin",

  organization := "com.github.mercurievv",
  scalaVersion := "2.12.10",
  crossScalaVersions := Seq("2.12.10"/*, "2.13.1"*/),

  // More version specific compiler options
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v <= 12 =>
      Seq(
        "-Ypartial-unification",
      )
    case _ =>
      Seq(
        // Replaces macro-paradise in Scala 2.13
        "-Ymacro-annotations",
      )
  }),

    // Turning off fatal warnings for doc generation
  scalacOptions.in(Compile, doc) ~= filterConsoleScalacOptions,
  // Silence all warnings from src_managed files
  scalacOptions += "-P:silencer:pathFilters=.*[/]src_managed[/].*",

  addCompilerPlugin("org.typelevel" % "kind-projector" % KindProjectorVersion cross CrossVersion.constant("2.12.10")),
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % BetterMonadicForVersion),
  addCompilerPlugin("com.github.ghik" % "silencer-plugin" % SilencerVersion cross CrossVersion.constant("2.12.10")),

  // ScalaDoc settings
  autoAPIMappings := true,
  scalacOptions in ThisBuild ++= Seq(
    // Note, this is used by the doc-source-url feature to determine the
    // relative path of a given source file. If it's not a prefix of a the
    // absolute path of the source file, the absolute path of that file
    // will be put into the FILE_SOURCE variable, which is
    // definitely not what we want.
    "-sourcepath", file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),

  // https://github.com/sbt/sbt/issues/2654
  incOptions := incOptions.value.withLogRecompileOnMacro(false),

  // ---------------------------------------------------------------------------
  // Options for testing

  testFrameworks += new TestFramework("minitest.runner.Framework"),
  logBuffered in Test := false,
  logBuffered in IntegrationTest := false,
  // Disables parallel execution
  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false,
  testForkedParallel in Test := false,
  testForkedParallel in IntegrationTest := false,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),

  // ---------------------------------------------------------------------------
  // Options meant for publishing on Maven Central

  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false }, // removes optional dependencies

  licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
  homepage := Some(url(projectWebsiteFullURL.value)),
  headerLicense := Some(HeaderLicense.Custom(
    s"""|Copyright (c) 2020 the ${projectTitle.value} contributors.
        |See the project homepage at: ${projectWebsiteFullURL.value}
        |
        |Licensed under the Apache License, Version 2.0 (the "License");
        |you may not use this file except in compliance with the License.
        |You may obtain a copy of the License at
        |
        |    http://www.apache.org/licenses/LICENSE-2.0
        |
        |Unless required by applicable law or agreed to in writing, software
        |distributed under the License is distributed on an "AS IS" BASIS,
        |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
        |See the License for the specific language governing permissions and
        |limitations under the License."""
      .stripMargin)),

  scmInfo := Some(
    ScmInfo(
      url(s"https://github.com/${githubFullRepositoryID.value}"),
      s"scm:git@github.com:${githubFullRepositoryID.value}.git"
    )),

  developers := List(
    Developer(
      id="MercurieVV",
      name="Viktors Kalinins",
      email="mercurievv@gmail.com",
      url=url("https://github.com/MercurieVV")
    )),

  // -- Settings meant for deployment on oss.sonatype.org
  sonatypeProfileName := organization.value,
)

/**
  * Shared configuration across all sub-projects with actual code to be published.
  */
def defaultCrossProjectConfiguration(pr: Project) = {
  val sharedJavascriptSettings = Seq(
    coverageExcludedFiles := ".*",
    // Use globally accessible (rather than local) source paths in JS source maps
    scalacOptions += {
      val tagOrHash = {
        val ver = s"v${version.value}"
        if (isSnapshot.value)
          git.gitHeadCommit.value.getOrElse(ver)
        else
          ver
      }
      val l = (baseDirectory in LocalRootProject).value.toURI.toString
      val g = s"https://raw.githubusercontent.com/${githubFullRepositoryID.value}/$tagOrHash/"
      s"-P:scalajs:mapSourceURI:$l->$g"
    }
  )

  val sharedJVMSettings = Seq(
    skip.in(publish) := customScalaJSVersion.isDefined
  )

  pr.configure(defaultPlugins)
    .settings(sharedSettings)
//    .jsSettings(sharedJavascriptSettings)
    .settings(doctestTestSettings(DoctestTestFramework.Minitest))
//    .jvmSettings(sharedJVMSettings)
    .settings(crossVersionSharedSources)
    .settings(coverageSettings)
}
/*

/**
  * Shared configuration across all sub-projects with actual code to be published.
  */
def defaultCrossProjectConfiguration(pr: CrossProject) = {
  val sharedJavascriptSettings = Seq(
    coverageExcludedFiles := ".*",
    // Use globally accessible (rather than local) source paths in JS source maps
    scalacOptions += {
      val tagOrHash = {
        val ver = s"v${version.value}"
        if (isSnapshot.value)
          git.gitHeadCommit.value.getOrElse(ver)
        else
          ver
      }
      val l = (baseDirectory in LocalRootProject).value.toURI.toString
      val g = s"https://raw.githubusercontent.com/${githubFullRepositoryID.value}/$tagOrHash/"
      s"-P:scalajs:mapSourceURI:$l->$g"
    }
  )

  val sharedJVMSettings = Seq(
    skip.in(publish) := customScalaJSVersion.isDefined
  )

  pr.configure(defaultPlugins)
    .settings(sharedSettings)
//    .jsSettings(sharedJavascriptSettings)
    .jvmSettings(doctestTestSettings(DoctestTestFramework.Minitest))
    .jvmSettings(sharedJVMSettings)
    .settings(crossVersionSharedSources)
    .settings(coverageSettings)
}
*/

lazy val root = project.in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
//  .aggregate(coreJVM/*, coreJS*/)
  .configure(defaultPlugins)
  .settings(sharedSettings)
//  .settings(doNotPublishArtifact)
//  .configureCross(defaultCrossProjectConfiguration)
  .settings(unidocSettings(project))
  .settings(
    // Try really hard to not execute tasks in parallel ffs
    Global / concurrentRestrictions := Tags.limitAll(1) :: Nil,
  )
  .settings(
    name := "sbt-openapi-generator-plugin-core",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "org.typelevel"  %% "cats-core"        % CatsVersion,
      "org.typelevel"  %% "cats-effect"      % CatsEffectVersion,
      "org.openapitools" % "openapi-generator" % "4.2.2",

      // For testing
      "org.scalatest" %% "scalatest" % "3.1.0" % Test,
      "io.monix"       %% "minitest"         % MinitestVersion % Test,
      "io.monix"       %% "minitest-laws"    % MinitestVersion % Test,
      "org.scalacheck" %% "scalacheck"       % ScalaCheckVersion % Test,
      "org.typelevel"  %% "cats-laws"        % CatsVersion % Test,
      "org.typelevel"  %% "cats-effect-laws" % CatsEffectVersion % Test,
    ),
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .settings(sharedSettings)
  .settings(doNotPublishArtifact)
//  .dependsOn(coreJVM)
  .settings {
    import microsites._
    Seq(
      micrositeName := projectTitle.value,
      micrositeDescription := "Plugin to generate code from swagger/openapi specs",
      micrositeAuthor := "Viktors Kalinins",
      micrositeTwitterCreator := "@MercurieVV",
      micrositeGithubOwner := githubOwnerID.value,
      micrositeGithubRepo := githubRelativeRepositoryID.value,
      micrositeUrl := projectWebsiteRootURL.value.replaceAll("[/]+$", ""),
      micrositeBaseUrl := projectWebsiteBasePath.value.replaceAll("[/]+$", ""),
      micrositeDocumentationUrl := s"${projectWebsiteFullURL.value.replaceAll("[/]+$", "")}/${docsMappingsAPIDir.value}/",
      micrositeGitterChannelUrl := githubFullRepositoryID.value,
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      micrositeCompilingDocsTool := WithMdoc,
      fork in mdoc := true,
      scalacOptions.in(Tut) ~= filterConsoleScalacOptions,
      libraryDependencies += "com.47deg" %% "github4s" % "0.21.0",
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFiles := Map(
        file("CODE_OF_CONDUCT.md") -> ExtraMdFileConfig("CODE_OF_CONDUCT.md", "page", Map("title" -> "Code of Conduct",   "section" -> "code of conduct", "position" -> "100")),
        file("LICENSE.md") -> ExtraMdFileConfig("LICENSE.md", "page", Map("title" -> "License",   "section" -> "license",   "position" -> "101"))
      ),
      docsMappingsAPIDir := s"api",
      addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc) in root, docsMappingsAPIDir),
      sourceDirectory in Compile := baseDirectory.value / "src",
      sourceDirectory in Test := baseDirectory.value / "test",
      mdocIn := (sourceDirectory in Compile).value / "mdoc",

      // Bug in sbt-microsites
      micrositeConfigYaml := microsites.ConfigYml(
        yamlCustomProperties = Map("exclude" -> List.empty[String])
      ),
    )
  }

/*
lazy val core = crossProject(/*JSPlatform, */JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .configureCross(defaultCrossProjectConfiguration)
  .settings(
    name := "sbt-openapi-generator-plugin-core",
    libraryDependencies ++= Seq(
      "org.typelevel"  %%% "cats-core"        % CatsVersion,
      "org.typelevel"  %%% "cats-effect"      % CatsEffectVersion,
      "org.openapitools" % "openapi-generator" % "4.2.2",

// For testing
      "org.scalatest" %% "scalatest" % "3.1.0" % Test,
      "io.monix"       %%% "minitest"         % MinitestVersion % Test,
      "io.monix"       %%% "minitest-laws"    % MinitestVersion % Test,
      "org.scalacheck" %%% "scalacheck"       % ScalaCheckVersion % Test,
      "org.typelevel"  %%% "cats-laws"        % CatsVersion % Test,
      "org.typelevel"  %%% "cats-effect-laws" % CatsEffectVersion % Test,
    ),
  )

lazy val coreJVM = core.jvm
//lazy val coreJS  = core.js
*/

// Reloads build.sbt changes whenever detected
Global / onChangedBuildSource := ReloadOnSourceChanges
