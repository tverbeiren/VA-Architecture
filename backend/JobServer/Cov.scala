package spark.jobserver

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark._
import org.apache.spark.SparkContext._
import scala.util.Try
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

import com.esotericsoftware.kryo.Kryo
import org.apache.spark.serializer.KryoRegistrator

class DataPoint(val position: Int, val coverage: Int)

// This is only required when using Kryo.
// Other parameters have to be set in order for this to be picked up
class MyRegistrator extends KryoRegistrator {
  override def registerClasses(kryo: Kryo) {
    kryo.register(classOf[DataPoint])
  }
}

object Cov extends SparkJob with NamedRddSupport {
  
  override def validate(sc: SparkContext, config: Config): SparkJobValidation = SparkJobValid

  override def runJob(sc: SparkContext, config: Config): Any = {

    val filename = config.getString("filename").toString
    val min_partitions = config.getString("partitions").toInt

  /* There are two ways here:
      1) just use caching, the RDD gets a number that can be retrieved from another job
      2) use namedRdds. In this case the specific interface has to be used
  */

    val rdd = namedRdds.getOrElseCreate("0", {
      val coverage = sc.textFile(filename,min_partitions)
      val covArray = coverage.map(_.split("\\s+")).map(x => new DataPoint(x(1).toInt, x(2).toInt))
      covArray
    })

    // force evaluation by retrieving the max
    val superMin = 1
    val superMax: Int = rdd.map(x => x.position).reduce((x, y) => Math.max(x, y))

    // return minimum, 
    //        maximum, 
    //        info about the cached RDD
    // Using a map, it is serialized to a JSON key-value object
    // This allows for getting the info in JS like <>.result.low
    // or <>.result.RDDs["3"] for info on RDD with number 3
    Map(("low",superMin), ("high",superMax), ("RDDs",sc.getPersistentRDDs))
  }

}
