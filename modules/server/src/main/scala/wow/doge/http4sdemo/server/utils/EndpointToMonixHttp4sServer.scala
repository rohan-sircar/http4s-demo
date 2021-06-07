// package sttp.tapir.server.http4s

// import cats.~>
// import cats.data._
// import cats.effect.{Concurrent, ContextShift, Sync, Timer}
// import cats.syntax.all._
// import org.http4s.{Http, HttpRoutes, Request, Response}
// import org.log4s._
// import sttp.tapir.server.internal.{DecodeBody, DecodeInputsResult, InputValues, InputValuesResult}
// import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults, ServerEndpoint, internal}
// import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput}
// import cats.arrow.FunctionK
// import sttp.capabilities.WebSockets
// import sttp.monad.MonadError
// import sttp.tapir.server.http4s._
// import scala.annotation.nowarn
// import sttp.capabilities.monix.MonixStreams

// @nowarn
// class EndpointToMonixHttp4sServer[F[_]: Concurrent: ContextShift: Timer](serverOptions: Http4sServerOptions[F]) {
//   private val outputToResponse = new OutputToHttp4sResponse[F](serverOptions)

//   def toHttp[I, E, O, G[_]: Sync](t: F ~> G, se: ServerEndpoint[I, E, O, MonixStreams with WebSockets, G]): Http[OptionT[G, *], F] = {
//     val monadError = new CatsMonadError[G]

//     val decodeBody = new DecodeBody[Request[F], G]()(monadError) {
//       override def rawBody[R](request: Request[F], body: EndpointIO.Body[R, _]): G[R] =
//         t(new Http4sRequestToRawBody(serverOptions).apply(request.body, body.bodyType, request.charset, request))
//     }

//     def valueToResponse(value: Any): G[Response[F]] = {
//       val i = value.asInstanceOf[I]
//       se.logic(monadError)(i)
//         .flatMap {
//           case Right(result) => t(outputToResponse(ServerDefaults.StatusCodes.success, se.endpoint.output, result))
//           case Left(err)     => t(outputToResponse(ServerDefaults.StatusCodes.error, se.endpoint.errorOutput, err))
//         }
//         .flatTap { response => t(serverOptions.logRequestHandling.requestHandled(se.endpoint, response.status.code)) }
//         .onError { case e: Exception =>
//           t(serverOptions.logRequestHandling.logicException(se.endpoint, e))
//         }
//     }

//     Kleisli((req: Request[F]) =>
//       OptionT(decodeBody(req, internal.DecodeInputs(se.endpoint.input, new Http4sDecodeInputsContext[F](req))).flatMap {
//         case values: DecodeInputsResult.Values =>
//           InputValues(se.endpoint.input, values) match {
//             case InputValuesResult.Value(params, _)        => valueToResponse(params.asAny).map(_.some)
//             case InputValuesResult.Failure(input, failure) => t(handleDecodeFailure(se.endpoint, input, failure))
//           }
//         case DecodeInputsResult.Failure(input, failure) => t(handleDecodeFailure(se.endpoint, input, failure))
//       })
//     )
//   }

//   def toHttp[G[_]: Sync](t: F ~> G)(se: List[ServerEndpoint[_, _, _, MonixStreams with WebSockets, G]]): Http[OptionT[G, *], F] =
//     NonEmptyList.fromList(se.map(se => toHttp(t, se))) match {
//       case Some(routes) => routes.reduceK
//       case None         => Kleisli(_ => OptionT.none)
//     }

//   def toRoutes[I, E, O](se: ServerEndpoint[I, E, O, MonixStreams with WebSockets, F]): HttpRoutes[F] =
//     toHttp(FunctionK.id[F], se)

//   def toRoutes[I, E, O](serverEndpoints: List[ServerEndpoint[_, _, _, MonixStreams with WebSockets, F]]): HttpRoutes[F] =
//     toHttp(FunctionK.id[F])(serverEndpoints)

//   private def handleDecodeFailure[I](
//       e: Endpoint[_, _, _, _],
//       input: EndpointInput[_],
//       failure: DecodeResult.Failure
//   ): F[Option[Response[F]]] = {
//     val decodeFailureCtx = DecodeFailureContext(input, failure, e)
//     val handling = serverOptions.decodeFailureHandler(decodeFailureCtx)
//     handling match {
//       case DecodeFailureHandling.NoMatch =>
//         serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx).map(_ => None)
//       case DecodeFailureHandling.RespondWithResponse(output, value) =>
//         serverOptions.logRequestHandling
//           .decodeFailureHandled(e, decodeFailureCtx, value)
//           .flatMap(_ => outputToResponse(ServerDefaults.StatusCodes.error, output, value))
//           .map(_.some)
//     }
//   }
// }

// @nowarn
// private class CatsMonadError[F[_]](implicit F: Sync[F]) extends MonadError[F] {
//   override def unit[T](t: T): F[T] = F.pure(t)
//   override def map[T, T2](fa: F[T])(f: T => T2): F[T2] = F.map(fa)(f)
//   override def flatMap[T, T2](fa: F[T])(f: T => F[T2]): F[T2] = F.flatMap(fa)(f)
//   override def error[T](t: Throwable): F[T] = F.raiseError(t)
//   override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] = F.recoverWith(rt)(h)
//   override def eval[T](t: => T): F[T] = F.delay(t)
//   override def suspend[T](t: => F[T]): F[T] = F.suspend(t)
//   override def flatten[T](ffa: F[F[T]]): F[T] = F.flatten(ffa)
//   override def ensure[T](f: F[T], e: => F[Unit]): F[T] = F.guarantee(f)(e)
// }

// @nowarn
// object EndpointToHttp4sServer {
//   private val log: Logger = getLogger
// }
