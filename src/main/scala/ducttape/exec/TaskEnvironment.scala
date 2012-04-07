package ducttape.exec

import ducttape.versioner.WorkflowVersioner
import ducttape.workflow.RealTask
import ducttape.workflow.Realization
import ducttape.workflow.hacks.Gimme
import java.io.File

class TaskEnvironment(val dirs: DirectoryArchitect, val versions: WorkflowVersioner, val task: RealTask) {
  
  // grab input paths -- how are these to be resolved?
  // If this came from a branch point, its source vertex *might* not
  // no knowledge of that branch point. However, we might *still* have
  // knowledge of that branch point at the source:
  // 1) If that branch point is defined in multiple places (i.e. a factored HyperDAG)
  //    and this the second time along an unpacked path that we encountered that
  //    branch point, then we might still need to keep it. This state information
  //    along a path is maintained by the constraintFilter.
  // 2) If the visibility of a branch point X is conditioned on the choice by another
  //    branch point Y?
  // TODO: Move unit tests from LoonyBin to ducttape to test for these sorts of corner cases
  // TODO: Then associate Specs with edge info to link parent realizations properly
  //       (need realization FOR EACH E, NOT HE, since some vertices may have no knowlege of peers' metaedges)
  val inputs: Seq[(String, String)] = for( (inSpec, srcSpec, srcTaskDef, srcRealization) <- task.inputVals) yield {
    val srcReal = new Realization(srcRealization) // TODO: Hacky
    val srcVersion: Int = versions(srcTaskDef.name, srcReal)
    val inFile = dirs.getInFile(inSpec, task.realization, task.version,
                                srcSpec, srcTaskDef, srcReal, srcVersion)
    //System.err.println("For inSpec %s with srcSpec %s, got path: %s".format(inSpec,srcSpec,inFile))
    (inSpec.name, inFile.getAbsolutePath)
  }
    
  // set param values (no need to know source active branches since we already resolved the literal)
  // TODO: Can we get rid of srcRealization or are we resolving parameters incorrectly sometimes?
  val params: Seq[(String,String)] = for( (paramSpec, srcSpec, srcTaskDef, srcRealization) <- task.paramVals) yield {
    //err.println("For paramSpec %s with srcSpec %s, got value: %s".format(paramSpec,srcSpec,srcSpec.rval.value))
    (paramSpec.name, srcSpec.rval.value)
  }

  // assign output paths
  val outputs: Seq[(String, String)] = for(outSpec <- task.outputs) yield {
    val outFile = dirs.assignOutFile(outSpec, task.taskDef, task.realization, task.version)
    //err.println("For outSpec %s got path: %s".format(outSpec, outFile))
    (outSpec.name, outFile.getAbsolutePath)
  }

  val packageNames: Seq[String] = Gimme.getPackagesFromParams(params)
  val packageBuilds: Seq[BuildEnvironment] = {
    packageNames.map(name => new BuildEnvironment(dirs, versions.workflowVersion, name))
  }
  val packageEnvs: Seq[(String,String)] = {
    packageBuilds.map(build => (build.packageName, new File(build.buildDir, build.packageName).getAbsolutePath) )
  }

  // TODO: Add correct build paths to task env
  lazy val env = inputs ++ outputs ++ params ++ packageEnvs
  
  val where = dirs.assignDir(task.taskDef, task.realization, task.version)
  val stdoutFile = new File(where, "stdout.txt")
  val stderrFile = new File(where, "stderr.txt")
  val workDir = new File(where, "work")
  val exitCodeFile = new File(where, "exit_code.txt")
  val invalidatedFile = new File(where, "INVALIDATED")
}