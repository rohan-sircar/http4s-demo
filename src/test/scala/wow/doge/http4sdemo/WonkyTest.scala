package wow.doge.http4sdemo

import java.time.LocalDateTime

import io.scalaland.chimney.cats._
import io.scalaland.chimney.dsl._
import munit.FunSuite
import wow.doge.http4sdemo.implicits._
import wow.doge.http4sdemo.models.Book
import wow.doge.http4sdemo.slickcodegen.Tables
import wow.doge.http4sdemo.utils.RefinementValidation

class WonkyTest extends FunSuite {
  test("blah blah") {

    Tables
      .BooksRow(12, "Asfwasaqw", "awqwfqas", 12, LocalDateTime.now)
      .transformIntoF[RefinementValidation, Book]
      .toEither
      .left
      .map(_.iterator.foreach(println))
      .foreach(println)

  }
}
