import caliban.*
import caliban.schema.ArgBuilder.auto.given
import caliban.schema.Schema
import caliban.schema.Schema.auto.given
import zio.*

transparent trait Rejection extends RuntimeException, Product, Serializable {
  override def getMessage: String =
    scala.runtime.ScalaRunTime._toString(this)
}

case class DragonNotFound(id: Dragon.Id) extends Rejection
case class DragonVacationing(id: Dragon.Id) extends Rejection
case class DragonSick(reason: String) extends Rejection
case class DBError(underlying: Throwable) extends Rejection

// The business logic layer
final class ErrorSchemaDemo(repo: DemoRepo) {

  private def checkDragonExists(
      id: Dragon.Id
  ): IO[DBError | DragonNotFound, Unit] =
    repo
      .getDragons(DragonFilter(idsIn = Some(NonEmptyChunk.single(id))))
      .flatMap(dragons => ZIO.fromOption(dragons.find(_.id == id)).orElseFail(DragonNotFound(id)))
      .unit

  private def checkDragonAvailable(
      id: Dragon.Id
  ): IO[DBError | DragonVacationing, Unit] =
    repo.getSchedules(ScheduleFilter(dragonIdsIn = Some(NonEmptyChunk.single(id)))).flatMap {
      case _ :: _ => ZIO.fail(DragonVacationing(id))
      case Nil    => ZIO.unit
    }

  def rentDragon(id: Dragon.Id) =
    for {
      _ <- checkDragonExists(id)
      _ <- checkDragonAvailable(id)
      // _ <- ZIO.fail(DragonSick("Boom"))
      // Try adding another error and run `scala-cli test .`
      // To trigger graphql-schema.graphql automatic file update
    } yield Rental(id = Rental.Id(s"rental-${id.value}"), dragonId = id)
}

// A helper class used to extract the inferred types from IO.
// This is needed because you can't refer to the types E or A directly.
private case class Result[E, A](value: ? => IO[E, A]) extends AnyVal {
  type Error = E
  type Output = A
  type Combined = E | A
}

// If you are using GraphQL ...
final class GraphQLApiDemo(val errorSchemaDemo: ErrorSchemaDemo) {
  given Schema[Any, Throwable] =
    Schema.stringSchema.contramap(_.getMessage)

  // Extract the result type as RentDragonResult...
  val rentDragonResult = Result(errorSchemaDemo.rentDragon)
  type RentDragonResult =
    rentDragonResult.Combined // Union type inferred by compiler

  // And use it to automatically derive GraphQL union type
  given Schema[Any, RentDragonResult] =
    Schema
      .unionType[RentDragonResult]
      .rename("RentDragonResult")

  case class RentDragonArgs(id: String)
  case class Query(
      rentDragon: RentDragonArgs => UIO[RentDragonResult]
  )

  val api: GraphQL[Any] =
    graphQL(
      RootResolver(
        Query(
          rentDragon = args =>
            errorSchemaDemo
              .rentDragon(Dragon.Id(args.id))
              .fold[RentDragonResult](rejection => rejection, rental => rental)
        )
      )
    )

  // The list of possible errors, straight to your schema
  // Check the graphql-schema.graphql file!
  val renderedSchema: String = renderSchema[Query, Unit, Unit]()
}

// If you're using REST...
final class MatchExample(errorSchemaDemo: ErrorSchemaDemo) {
  val result = errorSchemaDemo.rentDragon(Dragon.Id("smaug"))

  // Compiler checks exhaustive matches & unreachable cases
  // -Wconf:name=PatternMatchExhaustivity:e,name=MatchCaseUnreachable:e
  // Wconf all will work
  // Build your REST responses here
  val handled: UIO[String] =
    result.either.map {
      case Left(e: DragonNotFound) => s"404:not-found:${e.id}" // Try commenting
      case Left(e: DragonVacationing) => s"400:vacationing:${e.id}"
      case Left(e: DBError) => s"500:internal-server-error"
      // case Left(e: DragonSick)        => s"500:internal-server-error" // Try uncommenting me
      case Right(rental) => s"rented:${rental.id}"
    }
}
