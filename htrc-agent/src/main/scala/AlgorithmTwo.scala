
package htrcagent

/*
 * This is a dummy Algorithm class to test compute children with.
 */

class AlgorithmTwo {

  def initialize(): Unit = {
    
    println("Initializing...")
    Thread.sleep(3000)
    println("Intialized.")
    
  }
  
  def execute(): Unit = {
    
    println("Started working...")
    Thread.sleep(1000)
    println("Working...")
    Thread.sleep(4000)
    println("Finished working.")
    
  }
  
}

