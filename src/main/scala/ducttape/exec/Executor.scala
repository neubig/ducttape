package ducttape.exec

import collection._
import ducttape.util.Shell
import ducttape.util.Files
import ducttape.workflow.Realization
import ducttape.workflow.RealTask
import ducttape.workflow.HyperWorkflow
import ducttape.util.BashException

// workflow used for viz
class Executor(val dirs: DirectoryArchitect,
//               versions: WorkflowVersioner,
               val packageVersioner: PackageVersioner,
               val workflow: HyperWorkflow,
               val plannedVertices: Set[(String,Realization)],
               val alreadyDone: Set[(String,Realization)],
               val todo: Set[(String,Realization)],
               observers: Seq[ExecutionObserver] = Nil) extends UnpackedDagVisitor {
  import ducttape.viz.WorkflowViz
  
  val submitter = new Submitter(workflow.submitters)

  observers.foreach(_.init(this))

  override def visit(task: RealTask) {
    if (todo( (task.name, task.realization) )) {
      val taskEnv = new FullTaskEnvironment(dirs, packageVersioner, task)
      System.err.println("Running %s in %s".format(task.name, taskEnv.where.getAbsolutePath))
      try {  
        observers.foreach(_.begin(this, task))
        taskEnv.where.mkdirs
        if (!taskEnv.where.exists) {
          observers.foreach(_.fail(this, task))
          throw new BashException("Could not make directory: " + taskEnv.where.getAbsolutePath)
        }
  
        // the "run" action of the submitter will throw if the exit code is non-zero
        submitter.run(taskEnv)
      } finally {
        // TODO: Factor out into listener/callback?
         taskEnv.lockFile.delete()
      }
    }
    observers.foreach(_.complete(this, task))
  }
}