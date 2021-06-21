// package wow.doge.http4sdemo

// import wow.doge.http4sdemo.server.UnitTestBase
// import wow.doge.http4sdemo.server.utils.S3ClientResource
// import wow.doge.http4sdemo.implicits._
// import monix.bio.Task
// import java.nio.charset.StandardCharsets
// import scala.collection.immutable.ArraySeq
// import monix.reactive.Observable

// final class S3ClientTest extends UnitTestBase {
//   val fixture = ResourceFixture(S3ClientResource())

//   fixture.test("minofoobar") { s3 =>
//     // Observable(1, 2, 3).
//     // fs2.Stream[Task, Int](1, 2, 3).compile.to(Array)
//     val task: Task[Unit] =
//       // s3.createBucket("bucket1").toIO.void
//       s3.upload(
//         "bucket1",
//         "text/file.txt",
//         "hello world".getBytes(StandardCharsets.UTF_8)
//       ).toIO
//         .void
//     task
//   }
// }
