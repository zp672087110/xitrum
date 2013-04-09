package xitrum

import scala.util.control.NonFatal
import akka.actor.Actor

import org.jboss.netty.channel.{ChannelFuture, ChannelFutureListener}
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}

import xitrum.action._
import xitrum.exception.{InvalidAntiCSRFToken, InvalidInput, MissingParam, SessionExpired}
import xitrum.handler.{AccessLog, HandlerEnv}
import xitrum.handler.down.ResponseCacher
import xitrum.handler.up.Dispatcher
import xitrum.routing.Routes
import xitrum.scope.request.RequestEnv
import xitrum.scope.session.{CSRF, SessionEnv}
import xitrum.view.{Renderer, Responder}

/**
 * Action is designed to be separated from ActionActor, so that an ActionActor
 * can pass the Action outside without violating the principle of Actor:
 * Do not leak "this" of an Actor to outside.
 */
trait Action extends RequestEnv
  with SessionEnv
  with Logger
  with Net
  with Filter
  with BasicAuth
  with WebSocket
  with Redirect
  with UrlFor
  with Renderer
  with Responder
  with I18n
{
  implicit val currentAction = Action.this

  /**
   * Called when the HTTP request comes in.
   * Actions have to implement this method.
   */
  def execute()

  def addConnectionClosedListener(listener: => Unit) {
    channel.getCloseFuture.addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture) { listener }
    })
  }

  //----------------------------------------------------------------------------

  def dispatchWithFailsafe() {
    val beginTimestamp = System.currentTimeMillis()
    val route          = handlerEnv.route
    val cacheSecs      = if (route == null) 0 else route.cacheSecs
    var hit            = false

    try {
      // Check for CSRF (CSRF has been checked if "postback" is true)
      if ((request.getMethod == HttpMethod.POST ||
           request.getMethod == HttpMethod.PUT ||
           request.getMethod == HttpMethod.DELETE) &&
          !isInstanceOf[SkipCSRFCheck] &&
          !CSRF.isValidToken(Action.this)) throw new InvalidAntiCSRFToken

      // Before filters:
      // When not passed, the before filters must explicitly respond to client,
      // with appropriate response status code, error description etc.
      // This logic is app-specific, Xitrum cannot does it for the app.

      if (cacheSecs > 0) {     // Page cache
        hit = tryCache {
          val passed = callBeforeFilters()
          if (passed) runAroundAndAfterFilters()
        }
      } else {
        val passed = callBeforeFilters()
        if (passed) {
          if (cacheSecs < 0)  // Action cache
            hit = tryCache { runAroundAndAfterFilters() }
          else                // No cache
            runAroundAndAfterFilters()
        }
      }

      if (!forwarding) AccessLog.logDynamicContentAccess(Action.this, beginTimestamp, cacheSecs, hit)
    } catch {
      case NonFatal(e) =>
        if (forwarding) {
          logger.warn("Error", e)
          return
        }

        // End timestamp
        val t2 = System.currentTimeMillis()

        // These exceptions are special cases:
        // We know that the exception is caused by the client (bad request)
        if (e.isInstanceOf[SessionExpired] || e.isInstanceOf[InvalidAntiCSRFToken] || e.isInstanceOf[MissingParam] || e.isInstanceOf[InvalidInput]) {
          response.setStatus(HttpResponseStatus.BAD_REQUEST)
          val msg = if (e.isInstanceOf[SessionExpired] || e.isInstanceOf[InvalidAntiCSRFToken]) {
            session.clear()
            "Session expired. Please refresh your browser."
          } else if (e.isInstanceOf[MissingParam]) {
            val mp  = e.asInstanceOf[MissingParam]
            "Missing param: " + mp.key
          } else {
            val ve = e.asInstanceOf[InvalidInput]
            "Validation error: " + ve.message
          }

          if (isAjax)
            jsRespond("alert(\"" + jsEscape(msg) + "\")")
          else
            respondText(msg)

          AccessLog.logDynamicContentAccess(Action.this, beginTimestamp, 0, false)
        } else {
          response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR)
          if (Config.productionMode) {
            if (Routes.error500 == null) {
              respondDefault500Page()
            } else {
              if (Routes.error500 == getClass) {
                respondDefault500Page()
              } else {
                response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR)
                Dispatcher.dispatch(Routes.error500, handlerEnv)
              }
            }
          } else {
            val errorMsg = e.toString + "\n\n" + e.getStackTraceString
            if (isAjax)
              jsRespond("alert(\"" + jsEscape(errorMsg) + "\")")
            else
              respondText(errorMsg)
          }

          AccessLog.logDynamicContentAccess(Action.this, beginTimestamp, 0, false, e)
        }
    }
  }

  /** @return true if the cache was hit */
  private def tryCache(f: => Unit): Boolean = {
    ResponseCacher.getCachedResponse(handlerEnv) match {
      case None =>
        f
        false

      case Some(response) =>
        channel.write(response)
        true
    }
  }

  private def runAroundAndAfterFilters() {
    callExecuteWrappedInAroundFilters()
    callAfterFilters()
  }
}
