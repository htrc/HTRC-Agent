
/*
#
# Copyright 2013 The Trustees of Indiana University
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
*/


package htrc.agent

// An actor that runs a dummy task at the shell and sends status information
// to the supervisor.

import akka.actor.{ Actor, Props, ActorRef }
import akka.util.Timeout
import scala.util.Random
import scala.concurrent.duration._
import akka.event.Logging
import akka.event.slf4j.Logger
import scala.sys.process.{ Process => SProcess }
import scala.sys.process.{ ProcessLogger => SProcessLogger }
import scala.sys.process.{ ProcessBuilder => SProcessBuilder }
import scala.sys.process.ProcessIO
import java.io.File
import java.io.PrintWriter
import java.io.InputStream
import akka.pattern.ask
import scala.concurrent._
import scala.collection.mutable.HashMap

class DummyTask(user: HtrcUser, inputs: JobInputs, id: JobId) extends Actor {

  import HtrcUtils._

  // actor configuration
  import context._
  implicit val timeout = Timeout(30 seconds)
  val log = Logging(context.system, this)

  log.debug("DUMMY_TASK_LAUNCHED\t{}\t{}\tJOB_ID: {}",
           user.name, inputs.ip, id)

  val supe = parent

  // map associating algorithm names with List[JobResult] constructed from
  // the map in DummyTaskPreCreatedResults which associates algorithm names with
  // job results represented as XML
  val jobResultsMap: Map[String, List[JobResult]] =
    DummyTaskPreCreatedResults.jobResXml map { case (algName, resultsXml) =>
      (algName, (resultsXml \ "result").map(r =>
        ResultParser.toResult(r)).toList)
    }

  // update the status of the job by sending messages to self after arbitrary
  // time intervals
  val behavior: PartialFunction[Any,Unit] = {
    case m: DummyTaskMessage => 
      m match {
        case DummyTaskStaging =>
          supe ! StatusUpdate(InternalStaging)
          val waitTime = getRandomIntInRange(4, 5);
          system.scheduler.scheduleOnce(waitTime seconds, self, 
                                        DummyTaskQueuedOnTarget)

        case DummyTaskQueuedOnTarget =>
          supe ! StatusUpdate(InternalQueuedOnTarget)
          val waitTime = getRandomIntInRange(5, 10);
          system.scheduler.scheduleOnce(waitTime seconds, self, 
                                        DummyTaskRunning)

        case DummyTaskRunning =>
          supe ! StatusUpdate(InternalRunning)
          val waitTime = getRandomIntInRange(5, 10);
          system.scheduler.scheduleOnce(waitTime seconds, self, 
                                        DummyTaskFinished)

        case DummyTaskFinished =>
          // the job results are chosen using the algorithm, name from a set
          // of pre-constructed job results
          supe ! StatusUpdate(InternalFinished(
            getJobResults(inputs.algorithm, jobResultsMap)))
      }
  }

  val unknown: PartialFunction[Any,Unit] = {
    case m =>
      log.error("DummyTask received unhandled message")
  }

  override def preStart(): Unit = {
    val minStagingTime = 1
    val maxStagingTime = 3
    val waitTime = getRandomIntInRange(minStagingTime, maxStagingTime)

    // update status to "Staging" by sending a message to self after an
    // arbitrary time interval
    system.scheduler.scheduleOnce(waitTime seconds, self, 
				  DummyTaskStaging)
  }

  def receive = behavior orElse unknown

  // return a random int in the specified range, inclusive of both min and max
  def getRandomIntInRange(min: Int, max: Int): Int = {
    Random.nextInt(max - min + 1) + min
  }

  def getJobResults(algName: String, jobResults: Map[String, List[JobResult]]):
      List[JobResult] = {
    // the default result is the value associated with an algorithm that is
    // known to be in jobResults
    val defaultAlg = "Marc_Downloader"
    val defaultResult = jobResults.get(defaultAlg).get
    jobResults.get(algName).getOrElse(defaultResult)
  }
}

// messages sent by DummyTask to itself to move from one stage of the job to
// the next
trait DummyTaskMessage
case object DummyTaskStaging extends DummyTaskMessage
case object DummyTaskQueuedOnTarget extends DummyTaskMessage
case object DummyTaskRunning extends DummyTaskMessage
case object DummyTaskFinished extends DummyTaskMessage

// contains pre-constructed job results used as results of DummyTasks
object DummyTaskPreCreatedResults {
  // map associating algorithm names with pre-constructed and saved job
  // results XML for the algorithm; each of the 11 HTRC algorithms has an
  // associated job results element
  val jobResXml = Map("Marc_Downloader" -> <results><result type="stdout.txt">leena/5fb65584-6fe9-4640-9e5a-0f74b077bd86/stdout.txt</result><result type="stderr.txt">leena/5fb65584-6fe9-4640-9e5a-0f74b077bd86/stderr.txt</result><result type="result.zip">leena/5fb65584-6fe9-4640-9e5a-0f74b077bd86/job_results/result.zip</result></results>,
    "EF_Rsync_Script_Generator" -> <results><result type="stdout.txt">leena/ad122dd6-d525-4d00-a290-d14f4bc983dd/stdout.txt</result><result type="stderr.txt">leena/ad122dd6-d525-4d00-a290-d14f4bc983dd/stderr.txt</result><result type="EF_Rsync.sh">leena/ad122dd6-d525-4d00-a290-d14f4bc983dd/job_results/EF_Rsync.sh</result></results>,
    "Meandre_Classification_NaiveBayes" -> <results><result type="stdout.txt">leena/88d3d92e-8bcb-478e-a48e-eec39f1d27a1/stdout.txt</result><result type="stderr.txt">leena/88d3d92e-8bcb-478e-a48e-eec39f1d27a1/stderr.txt</result><result type="train_confusion_matrix.csv.txt">leena/88d3d92e-8bcb-478e-a48e-eec39f1d27a1/job_results/train_confusion_matrix.csv.txt</result><result type="train_prediction.csv.txt">leena/88d3d92e-8bcb-478e-a48e-eec39f1d27a1/job_results/train_prediction.csv.txt</result><result type="test_confusion_matrix.csv.txt">leena/88d3d92e-8bcb-478e-a48e-eec39f1d27a1/job_results/test_confusion_matrix.csv.txt</result><result type="test_prediction.csv.txt">leena/88d3d92e-8bcb-478e-a48e-eec39f1d27a1/job_results/test_prediction.csv.txt</result></results>,
    "Meandre_Dunning_LogLikelihood_to_Tagcloud" -> <results><result type="stdout.txt">leena/aaeb4cb0-8356-4f9c-bcc6-334557303be7/stdout.txt</result><result type="stderr.txt">leena/aaeb4cb0-8356-4f9c-bcc6-334557303be7/stderr.txt</result><result type="dunning_under_represented.csv">leena/aaeb4cb0-8356-4f9c-bcc6-334557303be7/job_results/dunning_under_represented.csv</result><result type="dunning_tagcloud_under.html">leena/aaeb4cb0-8356-4f9c-bcc6-334557303be7/job_results/dunning_tagcloud_under.html</result><result type="dunning_over_represented.csv">leena/aaeb4cb0-8356-4f9c-bcc6-334557303be7/job_results/dunning_over_represented.csv</result><result type="dunning_tagcloud_over.html">leena/aaeb4cb0-8356-4f9c-bcc6-334557303be7/job_results/dunning_tagcloud_over.html</result></results>,
    "Meandre_OpenNLP_Date_Entities_To_Simile" -> <results><result type="stdout.txt">leena/b49053ff-10a4-4fe9-a240-026f6e618a1a/stdout.txt</result><result type="stderr.txt">leena/b49053ff-10a4-4fe9-a240-026f6e618a1a/stderr.txt</result><result type="date_entity_simile.html">leena/b49053ff-10a4-4fe9-a240-026f6e618a1a/job_results/date_entity_simile.html</result></results>,
    "Meandre_OpenNLP_Entities_List" -> <results><result type="stdout.txt">leena/7ac83565-ef77-47e6-9af4-537c09588a54/stdout.txt</result><result type="stderr.txt">leena/7ac83565-ef77-47e6-9af4-537c09588a54/stderr.txt</result><result type="named_entities_list.html">leena/7ac83565-ef77-47e6-9af4-537c09588a54/job_results/named_entities_list.html</result></results>,
    "Meandre_Spellcheck_Report_Per_Volume" -> <results><result type="stdout.txt">leena/3fe18772-4022-44d0-8209-e476e501ece1/stdout.txt</result><result type="stderr.txt">leena/3fe18772-4022-44d0-8209-e476e501ece1/stderr.txt</result><result type="replacement_rules.txt">leena/3fe18772-4022-44d0-8209-e476e501ece1/job_results/replacement_rules.txt</result><result type="misspellings_with_counts.txt">leena/3fe18772-4022-44d0-8209-e476e501ece1/job_results/misspellings_with_counts.txt</result><result type="misspellings.txt">leena/3fe18772-4022-44d0-8209-e476e501ece1/job_results/misspellings.txt</result><result type="spellcheck_report.html">leena/3fe18772-4022-44d0-8209-e476e501ece1/job_results/spellcheck_report.html</result></results>,
    "Meandre_Tagcloud" -> <results><result type="stdout.txt">leena/54f6082d-9181-4dbf-adfb-d6d341a7ff19/stdout.txt</result><result type="stderr.txt">leena/54f6082d-9181-4dbf-adfb-d6d341a7ff19/stderr.txt</result><result type="tagcloudtokencounts.csv">leena/54f6082d-9181-4dbf-adfb-d6d341a7ff19/job_results/tagcloudtokencounts.csv</result><result type="tagcloudtokencounts.html">leena/54f6082d-9181-4dbf-adfb-d6d341a7ff19/job_results/tagcloudtokencounts.html</result></results>,
    "Meandre_Tagcloud_with_Cleaning" -> <results><result type="stdout.txt">leena/069abc0c-cac4-4ce2-82df-491cb5f98593/stdout.txt</result><result type="stderr.txt">leena/069abc0c-cac4-4ce2-82df-491cb5f98593/stderr.txt</result><result type="tagcloudcleantokencounts.csv.txt">leena/069abc0c-cac4-4ce2-82df-491cb5f98593/job_results/tagcloudcleantokencounts.csv.txt</result><result type="tagcloudcleantokencounts.html">leena/069abc0c-cac4-4ce2-82df-491cb5f98593/job_results/tagcloudcleantokencounts.html</result></results>,
    "Meandre_Topic_Modeling" -> <results><result type="stdout.txt">leena/66b15def-e6bc-4822-8424-742aa88d9341/stdout.txt</result><result type="stderr.txt">leena/66b15def-e6bc-4822-8424-742aa88d9341/stderr.txt</result><result type="topic_top_words.xml">leena/66b15def-e6bc-4822-8424-742aa88d9341/job_results/topic_top_words.xml</result><result type="topic_tagclouds.html">leena/66b15def-e6bc-4822-8424-742aa88d9341/job_results/topic_tagclouds.html</result></results>,
    "Simple_Deployable_Word_Count" -> <results><result type="stdout.txt">leena/a907543e-8024-445a-841c-6a0e83609559/stdout.txt</result><result type="stderr.txt">leena/a907543e-8024-445a-841c-6a0e83609559/stderr.txt</result></results>
  )
}
