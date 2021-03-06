package de.dfki.deployment.online

import de.dfki.core.streaming.BatchFileInputDStream
import de.dfki.deployment.Deployment
import de.dfki.experiments.Params
import de.dfki.ml.pipelines.Pipeline
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat
import org.apache.spark.streaming.StreamingContext

/**
  * @author behrouz
  */
class OnlineDeployment(val streamBase: String,
                       val evaluation: String = "prequential",
                       val resultPath: String,
                       val daysToProcess: Array[Int],
                       otherParams: Params) extends Deployment {

  override def deploy(streamingContext: StreamingContext, pipeline: Pipeline) = {
    val streamingSource = new BatchFileInputDStream[LongWritable, Text, TextInputFormat](streamingContext, streamBase, days = daysToProcess)

    pipeline.model.setMiniBatchFraction(1.0)
    pipeline.model.setNumIterations(1)
    pipeline.model.setConvergenceTol(0.0)
    var time = 1


    while (!streamingSource.allFileProcessed()) {
      val start = System.currentTimeMillis()
      val rdd = streamingSource.generateNextRDD().get.map(_._2.toString)
      rdd.setName(s"Stream $time")
      rdd.cache()

      if (evaluation == "prequential") {
        // perform evaluation
        evaluateStream(pipeline, rdd, resultPath, "online")
      }
      pipeline.updateTransformTrain(rdd)
      decideToSavePipeline(pipeline, "online", otherParams, time)
      rdd.unpersist()
      time += 1
      val end = System.currentTimeMillis()
      val elapsed = end - start
      storeElapsedTime(elapsed, resultPath, "online")
    }
  }
}
