import JacocoBaseKeysPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

/**
 * JacocoAgentPlugin (no aggregation/merge)
 * ---------------------------------------
 * - Attaches JaCoCo agent to forked JVMs per module (Test + optional IntegrationTest)
 * - Writes per-module .exec files (no merging)
 * - Generates per-module reports
 * - Provides root helpers: jacocoCleanAll / jacocoReportAll that just iterate modules (no merge)
 */
object FilteredJacocoAgentPlugin extends AutoPlugin {
  object autoImport {
    val jacocoVersion    = settingKey[String]("JaCoCo version")
    val jacocoExecFile   = settingKey[File]("Per-module JaCoCo .exec file (Test)")
    val jacocoItExecFile = settingKey[File]("Per-module JaCoCo .exec file (IntegrationTest)")
    val jacocoReportDir  = settingKey[File]("Per-module report directory")
    val jacocoIncludes   = settingKey[Seq[String]]("Include patterns (JaCoCo syntax)")
    val jacocoExcludes   = settingKey[Seq[String]]("Exclude patterns (JaCoCo syntax)")
    val jacocoAppend     = settingKey[Boolean]("Append to existing .exec instead of overwrite (default: false)")
    val jacocoFailOnMissingExec =
      settingKey[Boolean]("Fail jacocoReport if .exec is missing (default: false – warn & skip)")

    val jacocoReportName = settingKey[String]("Title used for JaCoCo HTML report")

    // Root-only helpers (NO MERGE): just run per-module tasks across aggregated projects
    val jacocoCleanAll  = taskKey[Unit]("Run jacocoClean in all aggregated modules (no merge)")
    val jacocoReportAll = taskKey[Unit]("Run jacocoReport in all aggregated modules (no merge)")

    val jacocoSetUserDirToBuildRoot = settingKey[Boolean]("Mimic non-forked runs by setting -Duser.dir to the build root for forked tests")

    val jmfCoreVersion     = settingKey[String]("JMF core library version")
    val Jmf                = config("jmf").hide
    val jmfRewrite         = taskKey[File]("Rewrite compiled classes using JMF tool; returns output dir")
    val jmfOutDir          = settingKey[File]("JMF output base dir")
    val jmfRulesFile       = settingKey[File]("JMF rules file")
    val jmfCliMain         = settingKey[String]("Main class of the JMF CLI")
    val jmfDryRun          = settingKey[Boolean]("Dry-run rewriter")
    val jmfEnabled         = settingKey[Boolean]("Enable JMF rewriting")
    val jmfPrepareForTests = taskKey[Unit]("Run JMF rewrite when enabled (no self-ref to test)")
  }
  import autoImport.*

  override def requires = JacocoBaseKeysPlugin
  override def trigger  = noTrigger

  // ---- helper: all aggregated descendants (BFS), excluding the root itself
  private def aggregatedDescendants(e: Extracted, root: ProjectRef): Vector[ProjectRef] = {
    val s      = e.structure
    val seen   = scala.collection.mutable.LinkedHashSet[ProjectRef](root)
    val queue  = scala.collection.mutable.Queue[ProjectRef](root)
    while (queue.nonEmpty) {
      val ref = queue.dequeue()
      val kids = Project.getProject(ref, s).toList.flatMap(_.aggregate)
      kids.foreach { k => if (!seen(k)) { seen += k; queue.enqueue(k) } }
    }
    seen.toVector.tail // drop root
  }

  // ---- helper: only those that set jacocoPluginEnabled := true
  private def enabledUnder(state: State): Vector[ProjectRef] = {
    val e    = Project.extract(state)
    val here = e.currentRef
    val all  = aggregatedDescendants(e, here)            // children only (no root)
    all.filter { ref =>
      e.getOpt((ref / jacocoPluginEnabled): SettingKey[Boolean]).getOrElse(false)
    }
  }

  lazy val Jmf = config("jmf").extend(Compile)

  // ---- commands
  private lazy val jacocoCleanAllCmd  = Command.command("jacocoCleanAll") { state =>
    val targets = enabledUnder(state)
    if (targets.isEmpty) { println("[jacoco] nothing to clean (no enabled modules under this aggregate)."); state }
    else targets.foldLeft(state) { (st, ref) => Command.process(s"${ref.project}/jacocoClean", st) }
  }

  private lazy val jacocoReportAllCmd = Command.command("jacocoReportAll") { state0 =>
    val e         = Project.extract(state0)
    val current   = e.currentRef
    // your existing helper (enabled projects under current aggregate)
    val under     = enabledUnder(state0)

    // Also include current project if enabled
    val selfEnabled =
      e.getOpt(current / jacocoPluginEnabled).getOrElse(false)

    val targets = (if (selfEnabled) current +: under else under).distinct

    if (targets.isEmpty) {
      println("[jacoco] nothing to report (no enabled modules here)."); state0
    } else {
      targets.foldLeft(state0) { (st, ref) =>
        Command.process(s"${ref.project}/jacocoReport", st)
      }
    }
  }

  // ---- global defaults so keys exist everywhere (safe no-ops on projects without the plugin)
  override def buildSettings: Seq[Def.Setting[_]] = Seq(
    jacocoPluginEnabled := false, // overridden to true in projects that enable the plugin
    // register commands + a convenient alias like sbt-jacoco had
    commands ++= Seq(jacocoCleanAllCmd, jacocoReportAllCmd)
  )

  private def findOnCp(cp: Seq[Attributed[File]])(p: File => Boolean): Option[File] =
    cp.map(_.data).find(p)

  private def agentJar(cp: Seq[Attributed[File]]): File = {
    val files = cp.map(_.data)
    files.find(f => f.getName.startsWith("org.jacoco.agent-") && f.getName.contains("-runtime"))
      .orElse(files.find(f => f.getName.contains("jacoco") && f.getName.contains("agent") && f.getName.contains("runtime")))
      .orElse(files.find(f => f.getName.startsWith("org.jacoco.agent-") && f.getName.endsWith(".jar"))) // last resort
      .getOrElse(sys.error("JaCoCo runtime agent JAR not found on Test / dependencyClasspath"))
  }

  private def cliJar(cp: Seq[Attributed[File]]): File = {
    val files = cp.map(_.data)
    files.find(f => f.getName.startsWith("org.jacoco.cli-") && f.getName.contains("nodeps"))
      .orElse(files.find(_.getName.startsWith("org.jacoco.cli-"))) // fallback, but we won't use it
      .getOrElse(sys.error("org.jacoco.cli (nodeps) JAR not found on Test / dependencyClasspath"))
  }

  private val defaultIncludes = Seq("**")
  private val defaultExcludes = Seq("scala.*", "java.*", "sun.*", "jdk.*")

  override def projectSettings: Seq[Setting[_]] = Seq(
    jacocoPluginEnabled := false,

    // ---- coordinates
    jacocoVersion := "0.8.12",
    jmfCoreVersion := "0.1.7",
    libraryDependencies ++= Seq(
      // pull the agent with the runtime classifier (this is the actual -javaagent jar)
      ("org.jacoco" % "org.jacoco.agent" % jacocoVersion.value % Test).classifier("runtime"),
      ("org.jacoco" % "org.jacoco.cli"   % jacocoVersion.value % Test).classifier("nodeps"),
      "io.github.moranaapps" %% "jacoco-method-filter-core" % jmfCoreVersion.value % Jmf.name,
    ),
    jacocoSetUserDirToBuildRoot := true,

    // ---- defaults
    jacocoExecFile   := target.value / "jacoco" / "jacoco.exec",
    jacocoReportDir  := target.value / "jacoco" / "report",
    jacocoIncludes   := defaultIncludes,
    jacocoExcludes   := defaultExcludes,
    jacocoAppend     := false,
    jacocoFailOnMissingExec := false,

    jacocoReportName := {
      val moduleId = thisProject.value.id            // or: thisProjectRef.value.project
      s"Report: $moduleId - scala:${scalaVersion.value}"
    },

  // --- JMF tool wiring
    ivyConfigurations += Jmf,

    jmfOutDir   := target.value / "jmf",
    jmfRulesFile:= (ThisBuild / baseDirectory).value / "jmf-rules.txt",
    jmfCliMain  := "io.moranaapps.jacocomethodfilter.CoverageRewriter",
    jmfDryRun   := false,
    jmfEnabled  := true,

    // the rewrite task (your code, lightly cleaned)
    jmfRewrite := {
      // --- hoist all .value lookups BEFORE conditionals ---
      // ensure classes exist (safe to always do; test would compile anyway)
      val _          = (Compile / compile).value

      val rules      = jmfRulesFile.value
      val upd        = (Jmf / update).value   // hoisted
      val log        = streams.value.log
      val outRoot    = jmfOutDir.value
      val mainCls    = jmfCliMain.value
      val dryRun     = jmfDryRun.value
      val workDir    = baseDirectory.value
      val classesIn  = (Compile / classDirectory).value
      val rulesFile  = jmfRulesFile.value
      val enabled    = jacocoPluginEnabled.value

      // Compile classpath (scala-stdlib, scopt, your module classes, etc.)
      val compileCp: Seq[File] = Attributed.data((Compile / fullClasspath).value)

      // Jmf-resolved jars (your jacoco-method-filter-core, etc.)
      val jmfJars: Seq[File] = (Jmf / update).value.matching(artifactFilter(`type` = "jar")).distinct

      // Final runtime CP
      val cp: Seq[File] = (compileCp ++ jmfJars :+ (Compile / classDirectory).value).distinct
      val cpStr = cp.distinct.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)

      val javaBin = {
        val h = sys.props.get("java.home").getOrElse("")
        if (h.nonEmpty) new java.io.File(new java.io.File(h, "bin"), "java").getAbsolutePath else "java"
      }
      // ----------------------------------------------------

      if (!enabled) classesIn
      else if (!classesIn.exists) {
        log.warn(s"[jmf] compiled classes dir not found, skipping: ${classesIn.getAbsolutePath}"); classesIn
      } else {
        val hasClasses = (classesIn ** sbt.GlobFilter("*.class")).get.nonEmpty
        if (!hasClasses) { log.warn(s"[jmf] no .class files under ${classesIn.getAbsolutePath}; skipping."); classesIn }
        else if (!rules.exists) { log.warn(s"[jmf] rules file missing: ${rules.getAbsolutePath}; skipping."); classesIn }
        else {
          val outDir   = jmfOutDir.value / "classes-filtered"
          IO.delete(outDir); IO.createDirectory(outDir)

          log.info("[jmf] runtime CP:\n" + cp.map(f => s"  - ${f.getAbsolutePath}").mkString("\n"))

          val args = Seq(
            javaBin, "-cp", cpStr, jmfCliMain.value,
            "--in",   classesIn.getAbsolutePath,
            "--out",  outDir.getAbsolutePath,
            "--rules", rules.getAbsolutePath
          ) ++ (if (jmfDryRun.value) Seq("--dry-run") else Seq())

          log.info(s"[jmf] rewrite: ${args.mkString(" ")}")
          val code = scala.sys.process.Process(args, workDir).!
          if (code != 0) sys.error(s"[jmf] rewriter failed ($code)")
          outDir
        }
      }
    },

    // 1) preparatory task (already defined earlier)
    jmfPrepareForTests := Def.taskDyn {
      if (jmfEnabled.value) Def.task { jmfRewrite.value; () }
      else                  Def.task { () }
    }.value,

    Test / fullClasspath := Def.taskDyn {
      // Gather the usual ingredients
      val testOut    = (Test / classDirectory).value                      // test classes dir
      val mainOut    = (Compile / classDirectory).value                   // original main classes dir
      val deps       = (Test / internalDependencyClasspath).value
      val ext        = (Test / externalDependencyClasspath).value
      val unmanaged  = (Test / unmanagedClasspath).value
      val scalaJars  = (Test / scalaInstance).value.allJars.map(Attributed.blank(_)).toVector
      val resources  = (Test / resourceDirectories).value.map(Attributed.blank)

      def build(rewrittenOpt: Option[File]) = Def.task {
        val rewrittenDifferent = rewrittenOpt.filter(_ != mainOut)
        val prefix = rewrittenDifferent.toVector.map(Attributed.blank) :+ Attributed.blank(testOut)
        val rest = (deps ++ ext ++ scalaJars ++ resources ++ unmanaged)
          .filterNot(a => a.data == mainOut || a.data == testOut || rewrittenDifferent.exists(_ == a.data))
        (prefix ++ rest :+ Attributed.blank(mainOut))
      }

      if (jacocoPluginEnabled.value) build(Some(jmfRewrite.value))
      else                           build(None)
    }.value,

    // ---- fork so -javaagent is applied
    Test / fork := true,

    // Attach agent for Test
    Test / forkOptions := {
      val fo0      = (Test / forkOptions).value
      val rootDir  = (LocalRootProject / baseDirectory).value
      val baseFO   = fo0.withWorkingDirectory(rootDir) // keep tests running from repo root

      // pre-compute values (avoids sbt linter warning about .value inside if)
      val cp       = (Test / dependencyClasspath).value
      val agent    = agentJar(cp)
      val dest     = jacocoExecFile.value.getAbsolutePath
      val inc      = jacocoIncludes.value.mkString(":")
      val exc      = jacocoExcludes.value.mkString(":")
      val append   = if (jacocoAppend.value) "true" else "false"
      val agentOpt =
        s"-javaagent:${agent.getAbsolutePath}=destfile=$dest,append=$append,output=file,includes=$inc,excludes=$exc,inclbootstrapclasses=false,jmx=false"

      val log = streams.value.log
      log.info(s"[jacoco] setting fork working dir to: $rootDir")

      if (jacocoPluginEnabled.value) {
        log.info(s"[jacoco] agent jar: ${agent.getName} (enabled)")
        baseFO.withRunJVMOptions(baseFO.runJVMOptions :+ agentOpt)
      } else {
        log.info("[jacoco] disabled (jacocoPluginEnabled=false); NOT adding -javaagent")
        baseFO
      }
    },

    // Print one sanity line per test fork
    Test / testOptions += Tests.Setup { () =>
      val status =
        try {
          val rt = Class.forName("org.jacoco.agent.rt.RT")
          val m  = rt.getMethod("getAgent")
          m.invoke(null) // throws if not attached
          "attached"
        } catch {
          case _: ClassNotFoundException => "rt-jar-not-on-classpath"
          case _: Throwable              => "present-but-not-attached"
        }
      println(s"[jacoco] agent status: $status; user.dir=" + System.getProperty("user.dir"))
    },


    // ---- per-module clean
    jacocoClean := {
      val log    = streams.value.log
      val outDir = target.value / "jacoco"
      IO.delete(outDir)
      IO.createDirectory(outDir)
      IO.delete(jmfOutDir.value)

      // remove sbt-jacoco leftovers if they ever existed
      val instrDir = (Test / crossTarget).value / "jacoco" / "instrumented-classes"
      if (instrDir.exists) {
        log.info(s"[jacoco] removing sbt-jacoco leftovers: ${instrDir.getAbsolutePath}")
        IO.delete(instrDir)
      }
      log.info(s"[jacoco] cleaned: ${outDir.getAbsolutePath}")
    },

    // ---- per-module report (only this module, no merge)
    jacocoReport := {
      val log = streams.value.log
      val execFile = jacocoExecFile.value
      val reportDir = jacocoReportDir.value
      IO.createDirectory(reportDir)

      // PRE-compute (avoid linter warnings)
      val moduleName = name.value
      val baseDir = baseDirectory.value
      val failOnMiss = jacocoFailOnMissingExec.value
      val cp = (Test / dependencyClasspath).value
      val cli = cliJar(cp)
      val title = jacocoReportName.value

      // Class dirs (filter to existing)
      val classesIn   = (Compile / classDirectory).value
      val filteredDir = jmfOutDir.value / "classes-filtered"
      val mainClasses =
        if (jacocoPluginEnabled.value && filteredDir.exists) filteredDir
        else classesIn

      val classDirs = Seq(mainClasses).filter(_.exists)

      // Source dirs: unmanaged + managed (filter to existing)
      val unmanagedSrc = (Compile / unmanagedSourceDirectories).value
      val managedSrc = (Compile / managedSourceDirectories).value
      val srcDirs = (unmanagedSrc ++ managedSrc).filter(_.exists)

      if (!execFile.exists) {
        val msg = s"[jacoco] .exec not found for $moduleName: $execFile . Run tests first."
        if (failOnMiss) sys.error(msg) else { log.warn(msg); reportDir }
      } else if (classDirs.isEmpty) {
        log.warn(s"[jacoco] no class dirs for $moduleName; skipping report.")
        reportDir
      } else {
        // repeat flags per path
        val args = Seq("java","-jar", cli.getAbsolutePath, "report", execFile.getAbsolutePath) ++
          classDirs.flatMap(d => Seq("--classfiles",  d.getAbsolutePath)) ++
          srcDirs  .flatMap(d => Seq("--sourcefiles", d.getAbsolutePath)) ++
          Seq("--name", title,
            "--html", reportDir.getAbsolutePath,
            "--xml",  (reportDir / "jacoco.xml").getAbsolutePath,
            "--csv",  (reportDir / "jacoco.csv").getAbsolutePath)

        val exit = scala.sys.process.Process(args, baseDir).!
        if (exit != 0) sys.error("JaCoCo report generation failed")
        log.info(s"[jacoco] per-module HTML: ${reportDir / "index.html"}")
        reportDir
      }
    }
  )
}
