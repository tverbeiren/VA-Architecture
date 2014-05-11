package spark.jobserver

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark._
import org.apache.spark.SparkContext._
//import scala.util.Try
import org.apache.spark.rdd.RDD
import scala.math._
//import akka.actor.{ActorRef, Props, PoisonPill}
//import akka.actor.ActorSystem

//import scala.util.control.Breaks._
//import collection.JavaConverters._
//import scala.collection.JavaConversions._

import com.esotericsoftware.kryo.Kryo
import org.apache.spark.serializer.KryoRegistrator

object CovQuery extends SparkJob with NamedRddSupport {

  type Range = (Int, Int)
  type TreePath = List[Int]

  val B = 100     // number of bins, also N
  val Z = 10      // zoom level with every step

  override def validate(sc: SparkContext, config: Config): SparkJobValidation = SparkJobValid

  override def runJob(sc: SparkContext, config: Config): Any = {

    // Parameters, we expect in order:
    //   - number of RDD to reuse
    //   - chromosome
    //   - start position
    //   - end position
    //   - optional: TF
    val myRDD = config.getString("RDD").toString
    val chr = config.getString("chr").toString
    val path = config.getString("path").split(" ").toList.map(_.toInt)
    val low:Int = config.getString("low").toInt
    val high:Int = config.getString("high").toInt
    val function = config.getString("function").toString

    // a map with the persistent RDDs
    val listOfPersistentRDDs = sc.getPersistentRDDs

    // For numbered RDDs, we need something like this:
    // val myCachedRdd = listOfPersistentRDDs(myRDD).asInstanceOf[RDD[(String, Int, Int)]]

    // For namedRdds, the following is the way to do it
    // namedRdds.get returns Option[RDD], so we need an additional get.
    val chrCache = namedRdds.get[DataPoint](myRDD).get

    // Info about the interval
    val superMin = low
    val superMax = high

    val subRange: Range = calcRange(path,superMin,superMax)
    val mn = subRange._1
    val mx = subRange._2

    // The bin width, based on the number of bins (N)
    val delta = (mx - mn) / B

    // Only filter the region within the interval
    val chrCache1 = chrCache filter (x => (mn <= x.position) && (x.position <= mx))

    // Create the bins as the first argument of a new triple
    val x1 = chrCache1 map (x => (myDiv(x.position - mn, delta), x.position, x.coverage))

    // sum, max and min can be done on only the bin number and the coverage:
    val fork1 = x1.map(x => (x._1,x._3))

    // The 4 statistics
    val sumSet = fork1.reduceByKey(_ + _)
    val maxSet = fork1.reduceByKey(Math.max(_,_))
    val minSet = fork1.reduceByKey(Math.min(_,_))
    val countSet = x1.map(x => (x._1,1)).reduceByKey(_+_)

    // Join the data and flatten the (nested) result
    val joined = sumSet.join(maxSet).join(minSet).join(countSet)
    val collected = joined.collect().map(x => (x._1, flattenT3(x._2)))

    // The boundaries in the original coordinate can not be derived
    // from the data, because it can not cope with empty regions.
    val lowerboundRange = mn until mx by delta
    val lowerboundIndex = 0 until B
    val lowerbound = lowerboundIndex zip lowerboundRange

    // Now, we have to join both data sets...
    // This is not too hard in principle, it is just that
    // the result of the flatten operation returns a Product
    // and this has to be manually converted to a list in order
    // to avoid nesting of structures in the JSON output
    // The last column is the average, calculated using sum/count
    val dataMap =collected.map(x=> (x._1,List(x._2._1,x._2._2,x._2._3,x._2._4,x._2._1/x._2._4))).toMap.withDefaultValue(List(0,0,0,0,0))

    return lowerbound.map(x => (x._1,List(x._2) ++ dataMap(x._1)))

  }


// We need this function in order to convert the nested tuples from
// the joins to a List or Seq
implicit def flattenT3[A, B, C, D](t: (((A, B), C),D)): (A,B,C,D) = (t._1._1._1, t._1._1._2,t._1._2, t._2)
implicit def flattenT4[A, B, C, D, E](t: ((((A, B), C),D),E)): (A, B, C, D, E) = (t._1._1._1._1,t._1._1._1._2, t._1._1._2,t._1._2, t._2)

def calcRange(path: TreePath,l:Int,u:Int): Range = {
  def calcRangeAcc(p: TreePath, acc: Range): Range = {
    p match {
      case h :: Nil => acc
      case h :: t :: tail => {
        val delta = (acc._2 - acc._1) / Z
        val newRange = (acc._1 + t * delta, acc._1 + t * delta + delta)
        calcRangeAcc(t :: tail, newRange)
      }
      case _ => acc // can not occur
    }
  }
  calcRangeAcc(path, (l, u))
}

def myDiv(x: Int, y: Int): Int = {
  val q = 1.0 * x / y
  q.toInt
}


}
