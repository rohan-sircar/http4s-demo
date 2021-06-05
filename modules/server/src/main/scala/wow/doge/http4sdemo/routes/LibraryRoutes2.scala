package wow.doge.http4sdemo.routes

import cats.syntax.all._
import io.odin.Logger
import io.odin.syntax._
import monix.bio.IO
import monix.bio.Task
import monix.bio.UIO
import monix.reactive.Consumer
import monix.reactive.Observable
import org.http4s.EntityBody
import org.http4s.HttpRoutes
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.LibraryEndpoints
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.auth.AuthService
import wow.doge.http4sdemo.server.utils.observableToJsonStreamA
import wow.doge.http4sdemo.services.LibraryService
import wow.doge.http4sdemo.utils.observableFromByteStreamA
final class LibraryRoutes2(L: LibraryService, A: AuthService)(
    val logger: Logger[Task]
) extends ServerInterpreter {

  def getBookById(
      id: BookId
  )(implicit logger: Logger[Task]): IO[AppError2, Book] = for {
    _ <- logger.infoU(s"Retrieving book")
    mbBook <- L.getBookById(id)
    res <- mbBook match {
      case Some(value) => IO.pure(value)
      case None =>
        logger.warnU(s"Request for non-existent book") >>
          IO.raiseError(
            AppError2
              .EntityDoesNotExist(s"Book with id $id does not exist")
          )
    }
  } yield res

  val getBookByIdRoute: HttpRoutes[Task] =
    toRoutes(LibraryEndpoints.getBookById) { case (ctx, id) =>
      getBookById(id)(
        logger.withConstContext(Map("reqCtx" -> ctx.toString))
      ).attempt
    }

  // val x = LibraryEndpoints.authedGetBookById
  //   .serverLogicPart(enrichLogger)
  // .andThen { case (logger, (auth, id)) =>
  //   getBookById(id)(logger).attempt
  // }
  // .andThenPart((D: AuthDetails) => A.parseDetails(D)(logger).attempt)

  val authedGetBookByIdRoute = toRoutes(
    LibraryEndpoints.authedGetBookById
      .serverLogicPart(enrichLogger)
      .andThen { case (logger, (auth, id)) =>
        val io = A
          .parseDetails(auth)(logger)
          .flatMap(_ => getBookById(id)(logger))
        io.attempt
      }
  )

  def getBooks(
      pagination: Pagination
  )(implicit logger: Logger[Task]): UIO[Observable[Book]] = {
    for {
      _ <- logger.infoU(s"Retrieving books")
      books = L.getBooks(pagination)
    } yield books
  }

  val getBooksRoute: HttpRoutes[Task] =
    toRoutes(LibraryEndpoints.getBooks) { case (ctx, pagination) =>
      getBooks(pagination)(
        logger.withConstContext(Map("reqCtx" -> ctx.toString))
      )
        .flatMap(o => observableToJsonStreamA(o))
        .attempt
    }

  def createBook(
      nb: NewBook
  )(implicit logger: Logger[Task]): IO[AppError2, Book] = {
    for {
      _ <- logger.infoU(s"Creating book")
      book <- L.createBook(nb)
    } yield book
  }

  val createBookRoute: HttpRoutes[Task] =
    toRoutes(
      LibraryEndpoints.createBook
        .serverLogicPart(enrichLogger)
        .andThen { case (logger, nb) =>
          createBook(nb)(logger).attempt
        }
    )

  def createBooks(
      stream: EntityBody[Task]
  )(implicit logger: Logger[Task]): IO[AppError2, Int] = {
    // IO.deferAction(implicit s =>
    for {
      _ <- logger.infoU(s"Creating books")
      newBooks <- observableFromByteStreamA[NewBook](stream)
      numRows <- newBooks
        .bufferTumbling(50)
        .consumeWith(
          Consumer
            .foldLeftEval(NumRows(0)) { case (numRows, books) =>
              L
                .createBooks(books.toList)
                .flatMap {
                  case Some(value) =>
                    IO.pure(numRows :+ value)
                  case None =>
                    IO.terminate(new Exception("Something went wrong"))
                }
                .toTask
            }
        )
        .toIO
        .mapErrorPartial { case e: AppError2 => e }
        .tapError(e => logger.errorU(s"Error occured: $e"))
    } yield numRows.toInt
    // )
  }

  val createBooksRoute: HttpRoutes[Task] =
    toRoutes(
      LibraryEndpoints.createBooksWithStream
        .serverLogicPart(enrichLogger)
        .andThen { case (logger, nb) =>
          createBooks(nb)(logger).attempt
        }
    )

  val routes: HttpRoutes[Task] =
    authedGetBookByIdRoute <+> getBookByIdRoute <+> getBooksRoute <+> createBookRoute <+> createBooksRoute

}
