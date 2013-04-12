package xitrum.routing

import java.io.Serializable
import scala.collection.mutable.ArrayBuffer
import xitrum.Action

class SerializableRouteCollection extends Serializable {
  val firstGETs = ArrayBuffer[SerializableRoute]()
  val lastGETs  = ArrayBuffer[SerializableRoute]()
  val otherGETs = ArrayBuffer[SerializableRoute]()

  val firstPOSTs = ArrayBuffer[SerializableRoute]()
  val lastPOSTs  = ArrayBuffer[SerializableRoute]()
  val otherPOSTs = ArrayBuffer[SerializableRoute]()

  val firstPUTs = ArrayBuffer[SerializableRoute]()
  val lastPUTs  = ArrayBuffer[SerializableRoute]()
  val otherPUTs = ArrayBuffer[SerializableRoute]()

  val firstDELETEs = ArrayBuffer[SerializableRoute]()
  val lastDELETEs  = ArrayBuffer[SerializableRoute]()
  val otherDELETEs = ArrayBuffer[SerializableRoute]()

  val firstOPTIONSs = ArrayBuffer[SerializableRoute]()
  val lastOPTIONSs  = ArrayBuffer[SerializableRoute]()
  val otherOPTIONSs = ArrayBuffer[SerializableRoute]()

  val firstWEBSOCKETs = ArrayBuffer[SerializableRoute]()
  val lastWEBSOCKETs  = ArrayBuffer[SerializableRoute]()
  val otherWEBSOCKETs = ArrayBuffer[SerializableRoute]()

  // 404.html and 500.html are used by default
  var error404: Option[String] = None
  var error500: Option[String] = None

  def toRouteCollection = new RouteCollection(
    firstGETs.map(_.toRoute),       lastGETs.map(_.toRoute),       otherGETs.map(_.toRoute),
    firstPOSTs.map(_.toRoute),      lastPOSTs.map(_.toRoute),      otherPOSTs.map(_.toRoute),
    firstPUTs.map(_.toRoute),       lastPUTs.map(_.toRoute),       otherPUTs.map(_.toRoute),
    firstDELETEs.map(_.toRoute),    lastDELETEs.map(_.toRoute),    otherDELETEs.map(_.toRoute),
    firstOPTIONSs.map(_.toRoute),   lastOPTIONSs.map(_.toRoute),   otherOPTIONSs.map(_.toRoute),
    firstWEBSOCKETs.map(_.toRoute), lastWEBSOCKETs.map(_.toRoute), otherWEBSOCKETs.map(_.toRoute),
    error404.map(Class.forName(_).asInstanceOf[Class[Action]]),
    error500.map(Class.forName(_).asInstanceOf[Class[Action]])
  )
}
