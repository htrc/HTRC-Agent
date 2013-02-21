

package htrc.agent

// A local machine resource.

trait LocalResource extends Resource { this: AlgorithmType =>

  // What tasks are specific to the compute resource type?
  
  // Remote Staging: Where to move the data and in what format.
  // Scheduling: How to tell the job to run.
  // Status: How to fetch the status of the job.
  // Results: How to move the results.
  // Cleanup: How to clean up the job.
  // Abort: How to abort the job.
  
}
