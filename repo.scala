import anorm.*
import decrel.*
import decrel.reify.{zquery, zqueryNextSyntax}
import decrel.reify.zquery.*
import io.github.gaelrenoux.tranzactio.*
import io.github.gaelrenoux.tranzactio.anorm.*
import javax.sql.DataSource
import org.h2.jdbcx.JdbcDataSource
import zio.{Schedule => _, *}
import zio.Console.printLine

case class FetchLog(
    relation: String,
    keys: Vector[String]
)

case class DragonFilter(
    idsIn: Option[NonEmptyChunk[Dragon.Id]],
    breedIdsIn: Option[NonEmptyChunk[Breed.Id]] = None
)

case class BreedFilter(
    idsIn: Option[NonEmptyChunk[Breed.Id]]
)

case class ScheduleFilter(
    dragonIdsIn: Option[NonEmptyChunk[Dragon.Id]]
)

case class RentalFilter(
    idsIn: Option[NonEmptyChunk[Rental.Id]],
    dragonIdsIn: Option[NonEmptyChunk[Dragon.Id]] = None
)

trait DemoRepo {
  // Plural fetches as default (Avoid N+1)
  def getDragons(filter: DragonFilter): IO[DBError, List[Dragon]]
  def getBreeds(filter: BreedFilter): IO[DBError, List[Breed]]
  def getSchedules(filter: ScheduleFilter): IO[DBError, List[Schedule]]
  def getRentals(filter: RentalFilter): IO[DBError, List[Rental]]
}

final class H2DemoRepo private (
    val dataSource: DataSource
) extends DemoRepo {

  private case class SqlFilterPart(
      clause: String,
      parameter: NamedParameter
  )

  private var fetchLog: Vector[FetchLog] = Vector.empty

  private val dbLayer =
    ZLayer.succeed(dataSource) >>> Database.fromDatasource

  private val dragonParser: RowParser[Dragon] =
    SqlParser.str("id") ~
      SqlParser.str("name") ~
      SqlParser.str("breed_id") ~
      SqlParser.int("age") ~
      SqlParser.get[Option[String]]("schedule_id") map {
        case id ~ name ~ breedId ~ age ~ scheduleId =>
          Dragon(
            id = Dragon.Id(id),
            name = name,
            breedId = Breed.Id(breedId),
            age = age,
            scheduleId = scheduleId.map(Dragon.Id(_))
          )
      }

  private val breedParser: RowParser[Breed] =
    SqlParser.str("id") ~
      SqlParser.str("name") ~
      SqlParser.int("fire_resistance") ~
      SqlParser.int("max_altitude") map {
        case id ~ name ~ fireResistance ~ maxAltitude =>
          Breed(
            id = Breed.Id(id),
            name = name,
            fireResistance = fireResistance,
            maxAltitude = maxAltitude
          )
      }

  private val scheduleParser: RowParser[Schedule] =
    SqlParser.str("dragon_id") ~
      SqlParser.str("starts_at") ~
      SqlParser.str("ends_at") map {
        case dragonId ~ startsAt ~ endsAt =>
          Schedule(
            dragonId = Dragon.Id(dragonId),
            startsAt = startsAt,
            endsAt = endsAt
          )
      }

  private val rentalParser: RowParser[Rental] =
    SqlParser.str("id") ~
      SqlParser.str("dragon_id") map {
        case id ~ dragonId =>
          Rental(
            id = Rental.Id(id),
            dragonId = Dragon.Id(dragonId)
          )
      }

  private def run[A](query: ZIO[Connection, DbException, A]): IO[DBError, A] =
    Database
      .autoCommitOrWiden(query)
      .mapError(DBError(_))
      .provideLayer(dbLayer)

  private def appendFetchLog(entry: FetchLog): Unit = this.synchronized {
    fetchLog = fetchLog :+ entry
  }

  def fetchLogEntry(
      relation: String,
      keys: Chunk[String]
  ): UIO[Unit] = {
    val renderedKeys = keys.toVector

    ZIO.succeed(appendFetchLog(FetchLog(relation, renderedKeys))) *>
    printLine(
      s"[fetch] $relation ${renderedKeys.mkString("[", ", ", "]")}"
    ).orDie
  }

  def fetchLogSnapshot(): Vector[FetchLog] = this.synchronized {
    fetchLog
  }

  def clearFetchLog(): Unit = this.synchronized {
    fetchLog = Vector.empty
  }

  private def renderWhereClause(parts: List[SqlFilterPart]): String =
    parts match {
      case Nil => "TRUE"
      case xs  => xs.map(_.clause).mkString(" ( ", " ) AND ( ", " ) ")
    }

  private def renderParameters(parts: List[SqlFilterPart]): Seq[NamedParameter] =
    parts.map(_.parameter)

  def getDragons(filter: DragonFilter): IO[DBError, List[Dragon]] =
    run {
      tzio { implicit c =>
        val parts =
          List(
            filter.idsIn.map(ids =>
              SqlFilterPart(
                clause = "id IN ({ids})",
                parameter =
                  NamedParameter("ids", ids.distinct.map(_.value).toList)
              )
            ),
            filter.breedIdsIn.map(breedIds =>
              SqlFilterPart(
                clause = "breed_id IN ({breedIds})",
                parameter =
                  NamedParameter(
                    "breedIds",
                    breedIds.distinct.map(_.value).toList
                  )
              )
            )
          ).flatten

        SQL(
          s"""
          SELECT id, name, breed_id, age, schedule_id
          FROM dragon
          WHERE ${renderWhereClause(parts)}
          """
        ).on(renderParameters(parts)*)
          .as(dragonParser.*)
      }
    }

  def getBreeds(filter: BreedFilter): IO[DBError, List[Breed]] =
    run {
      tzio { implicit c =>
        val parts =
          List(
            filter.idsIn.map(ids =>
              SqlFilterPart(
                clause = "id IN ({ids})",
                parameter =
                  NamedParameter("ids", ids.distinct.map(_.value).toList)
              )
            )
          ).flatten

        SQL(
          s"""
          SELECT id, name, fire_resistance, max_altitude
          FROM breed
          WHERE ${renderWhereClause(parts)}
          """
        ).on(renderParameters(parts)*)
          .as(breedParser.*)
      }
    }

  def getSchedules(filter: ScheduleFilter): IO[DBError, List[Schedule]] =
    run {
      tzio { implicit c =>
        val parts =
          List(
            filter.dragonIdsIn.map(dragonIds =>
              SqlFilterPart(
                clause = "dragon_id IN ({dragonIds})",
                parameter =
                  NamedParameter(
                    "dragonIds",
                    dragonIds.distinct.map(_.value).toList
                  )
              )
            )
          ).flatten

        SQL(
          s"""
          SELECT dragon_id, starts_at, ends_at
          FROM schedule
          WHERE ${renderWhereClause(parts)}
          ORDER BY dragon_id, starts_at
          """
        ).on(renderParameters(parts)*)
          .as(scheduleParser.*)
      }
    }

  def getRentals(filter: RentalFilter): IO[DBError, List[Rental]] =
    run {
      tzio { implicit c =>
        val parts =
          List(
            filter.idsIn.map(ids =>
              SqlFilterPart(
                clause = "id IN ({ids})",
                parameter =
                  NamedParameter("ids", ids.distinct.map(_.value).toList)
              )
            ),
            filter.dragonIdsIn.map(dragonIds =>
              SqlFilterPart(
                clause = "dragon_id IN ({dragonIds})",
                parameter =
                  NamedParameter(
                    "dragonIds",
                    dragonIds.distinct.map(_.value).toList
                  )
              )
            )
          ).flatten

        SQL(
          s"""
          SELECT id, dragon_id
          FROM rental
          WHERE ${renderWhereClause(parts)}
          """
        ).on(renderParameters(parts)*)
          .as(rentalParser.*)
      }
    }
}

object H2DemoRepo {
  private def makeDataSource(): Task[DataSource] =
    ZIO.attempt {
      val dataSource = new JdbcDataSource()
      dataSource.setURL(
        s"jdbc:h2:mem:scalar2026-${java.util.UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
      )
      dataSource.setUser("sa")
      dataSource.setPassword("")
      dataSource
    }

  private def initialize(dataSource: DataSource): Task[Unit] = {
    val dbLayer =
      ZLayer.succeed(dataSource) >>> Database.fromDatasource

    Database.autoCommitOrWiden {
      tzio { implicit c =>
        val setupSql =
          """
          CREATE TABLE breed (
            id VARCHAR PRIMARY KEY,
            name VARCHAR NOT NULL,
            fire_resistance INT NOT NULL,
            max_altitude INT NOT NULL
          );

          CREATE TABLE dragon (
            id VARCHAR PRIMARY KEY,
            name VARCHAR NOT NULL,
            breed_id VARCHAR NOT NULL,
            age INT NOT NULL,
            schedule_id VARCHAR NULL
          );

          CREATE TABLE schedule (
            dragon_id VARCHAR NOT NULL,
            starts_at VARCHAR NOT NULL,
            ends_at VARCHAR NOT NULL
          );

          CREATE TABLE rental (
            id VARCHAR PRIMARY KEY,
            dragon_id VARCHAR NOT NULL
          );

          INSERT INTO breed (id, name, fire_resistance, max_altitude)
          VALUES ('fire-drake', 'Fire Drake', 95, 5000);

          INSERT INTO breed (id, name, fire_resistance, max_altitude)
          VALUES ('ice-wyrm', 'Ice Wyrm', 10, 8000);

          INSERT INTO dragon (id, name, breed_id, age, schedule_id)
          VALUES ('smaug', 'Smaug', 'fire-drake', 892, NULL);

          INSERT INTO dragon (id, name, breed_id, age, schedule_id)
          VALUES ('frostbite', 'Frostbite', 'ice-wyrm', 341, 'frostbite');

          INSERT INTO schedule (dragon_id, starts_at, ends_at)
          VALUES ('frostbite', '2026-04-01', '2026-04-14');

          INSERT INTO schedule (dragon_id, starts_at, ends_at)
          VALUES ('frostbite', '2026-05-01', '2026-05-07');

          INSERT INTO rental (id, dragon_id)
          VALUES ('rental-1', 'smaug');

          INSERT INTO rental (id, dragon_id)
          VALUES ('rental-2', 'frostbite');
          """

        val stmt = c.createStatement()
        try {
          stmt.execute(setupSql)
          ()
        } finally stmt.close()
      }
    }.provideLayer(dbLayer)
  }

  def make: Task[H2DemoRepo] =
    for {
      dataSource <- makeDataSource()
      _ <- initialize(dataSource)
    } yield new H2DemoRepo(dataSource)
}

object DemoData {
  def repo: Task[H2DemoRepo] =
    H2DemoRepo.make
}

final class SelectionTreeProofs(val repo: H2DemoRepo)
    extends zquery[Any],
      zqueryNextSyntax[Any] {

  given rentalFetchProof: Proof.Single[
    Rental.fetch.type,
    Rental.Id,
    DBError,
    Rental
  ] =
    implementSingleDatasource(Rental.fetch) { ins =>
      repo.fetchLogEntry("Rental.fetch", ins.map(_.value)) *>
      (NonEmptyChunk.fromChunk(ins) match {
        case Some(ids) =>
          repo.getRentals(RentalFilter(idsIn = Some(ids))).flatMap { rentals =>
            ZIO.foreach(ins) { id =>
              ZIO
                .fromOption(rentals.find(_.id == id))
                .orElseFail(
                  DBError(
                    new NoSuchElementException(
                      s"Rental not found: ${id.value}"
                    )
                  )
                )
                .map(rental => id -> rental)
            }
          }
        case None => ZIO.succeed(Chunk.empty)
      })
    }

  given rentalDragonProof: Proof.Single[
    Rental.dragon.type,
    Rental,
    DBError,
    Dragon
  ] =
    implementSingleDatasource(Rental.dragon) { ins =>
      repo.fetchLogEntry("Rental.dragon", ins.map(_.id.value)) *>
      (NonEmptyChunk.fromChunk(ins.map(_.dragonId)) match {
        case Some(dragonIds) =>
          repo.getDragons(DragonFilter(idsIn = Some(dragonIds))).flatMap { dragons =>
            ZIO.foreach(ins) { rental =>
              ZIO
                .fromOption(dragons.find(_.id == rental.dragonId))
                .orElseFail(
                  DBError(
                    new NoSuchElementException(
                      s"Dragon not found: ${rental.dragonId.value}"
                    )
                  )
                )
                .map(dragon => rental -> dragon)
            }
          }
        case None => ZIO.succeed(Chunk.empty)
      })
    }

  given dragonBreedProof: Proof.Single[
    Dragon.breed.type,
    Dragon,
    DBError,
    Breed
  ] =
    implementSingleDatasource(Dragon.breed) { ins =>
      repo.fetchLogEntry("Dragon.breed", ins.map(_.breedId.value)) *>
      (NonEmptyChunk.fromChunk(ins.map(_.breedId)) match {
        case Some(breedIds) =>
          repo.getBreeds(BreedFilter(idsIn = Some(breedIds))).flatMap { breeds =>
            ZIO.foreach(ins) { dragon =>
              ZIO
                .fromOption(breeds.find(_.id == dragon.breedId))
                .orElseFail(
                  DBError(
                    new NoSuchElementException(
                      s"Breed not found: ${dragon.breedId.value}"
                    )
                  )
                )
                .map(breed => dragon -> breed)
            }
          }
        case None => ZIO.succeed(Chunk.empty)
      })
    }

  given breedDragonsProof: Proof.Many[
    Breed.dragons.type,
    Breed,
    DBError,
    List,
    Dragon
  ] =
    implementManyDatasource(Breed.dragons) { ins =>
      repo.fetchLogEntry("Breed.dragons", ins.map(_.id.value)) *>
      (NonEmptyChunk.fromChunk(ins.map(_.id)) match {
        case Some(breedIds) =>
          repo.getDragons(DragonFilter(idsIn = None, breedIdsIn = Some(breedIds))).map { dragons =>
            val dragonsByBreedId = dragons.groupBy(_.breedId)
            ins.map { breed =>
              breed -> dragonsByBreedId.getOrElse(breed.id, Nil)
            }
          }
        case None => ZIO.succeed(Chunk.empty)
      })
    }

  given dragonScheduleProof: Proof.Many[
    Dragon.schedule.type,
    Dragon,
    DBError,
    List,
    Schedule
  ] =
    implementManyDatasource(Dragon.schedule) { ins =>
      repo.fetchLogEntry("Dragon.schedule", ins.map(_.id.value)) *>
      (NonEmptyChunk.fromChunk(ins.map(_.id)) match {
        case Some(dragonIds) =>
          repo.getSchedules(ScheduleFilter(dragonIdsIn = Some(dragonIds))).map { schedules =>
            val schedulesByDragonId = schedules.groupBy(_.dragonId)
            ins.map { dragon =>
              dragon -> schedulesByDragonId.getOrElse(dragon.id, Nil)
            }
          }
        case None => ZIO.succeed(Chunk.empty)
      })
    }

  given dragonRentalsProof: Proof.Many[
    Dragon.rentals.type,
    Dragon,
    DBError,
    List,
    Rental
  ] =
    implementManyDatasource(Dragon.rentals) { ins =>
      repo.fetchLogEntry("Dragon.rentals", ins.map(_.id.value)) *>
      (NonEmptyChunk.fromChunk(ins.map(_.id)) match {
        case Some(dragonIds) =>
          repo.getRentals(RentalFilter(idsIn = None, dragonIdsIn = Some(dragonIds))).map { rentals =>
            val rentalsByDragonId = rentals.groupBy(_.dragonId)
            ins.map { dragon =>
              dragon -> rentalsByDragonId.getOrElse(dragon.id, Nil)
            }
          }
        case None => ZIO.succeed(Chunk.empty)
      })
    }

  given scheduleDragonProof: Proof.Single[
    Schedule.dragon.type,
    Schedule,
    DBError,
    Dragon
  ] =
    implementSingleDatasource(Schedule.dragon) { ins =>
      repo.fetchLogEntry("Schedule.dragon", ins.map(_.dragonId.value)) *>
      (NonEmptyChunk.fromChunk(ins.map(_.dragonId)) match {
        case Some(dragonIds) =>
          repo.getDragons(DragonFilter(idsIn = Some(dragonIds))).flatMap { dragons =>
            ZIO.foreach(ins) { schedule =>
              ZIO
                .fromOption(dragons.find(_.id == schedule.dragonId))
                .orElseFail(
                  DBError(
                    new NoSuchElementException(
                      s"Dragon not found: ${schedule.dragonId.value}"
                    )
                  )
                )
                .map(dragon => schedule -> dragon)
            }
          }
        case None => ZIO.succeed(Chunk.empty)
      })
    }
}
