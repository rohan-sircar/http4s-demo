package wow.doge.http4sdemo.server.routes

import cats.syntax.all._
import io.odin.Logger
import monix.bio.IO
import monix.bio.Task
import monix.reactive.Consumer
import monix.reactive.Observable
import org.http4s.HttpRoutes
import wow.doge.http4sdemo.AppError2
import wow.doge.http4sdemo.endpoints.LibraryEndpoints
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.models.NewBook
import wow.doge.http4sdemo.models.common.UserRole
import wow.doge.http4sdemo.models.pagination._
import wow.doge.http4sdemo.refinements.Refinements._
import wow.doge.http4sdemo.server.services.AuthService
import wow.doge.http4sdemo.server.services.LibraryService
import wow.doge.http4sdemo.server.utils.observableToJsonStreamA
import wow.doge.http4sdemo.utils.infoSpan
import wow.doge.http4sdemo.utils.observableFromByteStreamA

final class LibraryRoutes2(L: LibraryService, val authService: AuthService)(
    val logger: Logger[Task]
) extends AuthedServerInterpreter {

  def getBookById(
      id: BookId
  )(implicit logger: Logger[Task]): IO[AppError2, Book] = infoSpan {
    for {
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
  }

  val getBookByIdRoute: HttpRoutes[Task] =
    toRoutes(
      LibraryEndpoints.getBookById
        .serverLogicPart(enrichLogger)
        .andThenRecoverErrors { case (logger, id) =>
          getBookById(id)(logger)
        }
    )

  val authedGetBookByIdRoute =
    toRoutes(
      LibraryEndpoints.authedGetBookById
        .serverLogicRecoverErrors { case (ctx, details, id) =>
          authorize(ctx, details)(UserRole.SuperUser) {
            case (logger, authDetails) => getBookById(id)(logger)
          }.leftWiden[Throwable]
        }
    )

  def getBooks(
      pagination: Pagination
  )(implicit logger: Logger[Task]): IO[AppError2, Observable[Book]] = infoSpan {
    for {
      _ <- logger.infoU(s"Retrieving books")
      books <- L.getBooks(pagination)
    } yield books
  }

  val getBooksRoute: HttpRoutes[Task] =
    toRoutes(
      LibraryEndpoints.getBooks
        .serverLogicPart(enrichLogger)
        .andThenRecoverErrors { case (logger, pagination) =>
          getBooks(pagination)(logger)
            .flatMap(o => observableToJsonStreamA(o))
        }
    )

  def createBook(
      nb: NewBook
  )(implicit logger: Logger[Task]): IO[AppError2, Book] = infoSpan {
    for {
      _ <- logger.infoU(s"Creating book")
      book <- L.createBook(nb)
    } yield book
  }

  val createBookRoute: HttpRoutes[Task] =
    toRoutes(
      LibraryEndpoints.createBook
        .serverLogicPart(enrichLogger)
        .andThenRecoverErrors { case (logger, nb) =>
          createBook(nb)(logger)
        }
    )

  def createBooks(
      newBooks: Observable[NewBook]
  )(implicit logger: Logger[Task]): IO[AppError2, Int] = infoSpan {
    // IO.deferAction(implicit s =>
    for {
      _ <- logger.infoU(s"Creating books")
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
    } yield numRows.toInt
    // )
  }

  val createBooksRoute: HttpRoutes[Task] =
    toRoutes(
      LibraryEndpoints.createBooksWithStream
        .serverLogicPart(enrichLogger)
        .andThenRecoverErrors { case (logger, stream) =>
          observableFromByteStreamA[NewBook](stream).flatMap(obs =>
            createBooks(obs)(logger)
          )
        }
    )

  val routes: HttpRoutes[Task] =
    getBookByIdRoute <+> authedGetBookByIdRoute <+> getBookByIdRoute <+>
      getBooksRoute <+> createBookRoute <+> createBooksRoute

}
