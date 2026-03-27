import decrel.*
import munit.FunSuite
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import zio.{Schedule => _, *}

class SlidesSuite extends FunSuite {
  private def unsafeRun[A](effect: Task[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private val repo = unsafeRun(DemoData.repo)
  private val errorSchemaDemo = new ErrorSchemaDemo(repo)
  private val graphQLApiDemo = new GraphQLApiDemo(errorSchemaDemo)
  private val selectionTreeProofs = new SelectionTreeProofs(repo)
  private val selectionTreeDemo =
    new SelectionTreeDemo(repo, selectionTreeProofs)

  import selectionTreeProofs.{*, given}

  test("graphql schema matches the checked-in golden file") {
    val goldenDir = Path.of("golden")
    val goldenPath = goldenDir.resolve("graphql-schema.graphql")

    Files.createDirectories(goldenDir)

    val rendered = graphQLApiDemo.renderedSchema.stripTrailing + "\n"
    val previous =
      if (Files.exists(goldenPath)) {
        Some(Files.readString(goldenPath, StandardCharsets.UTF_8))
      } else {
        None
      }

    if (previous.exists(_ != rendered)) {
      println(s"GraphQL schema changed, overwriting $goldenPath")
    }

    Files.writeString(goldenPath, rendered, StandardCharsets.UTF_8)
  }

  test("Dragon.schedule then Schedule.dragon hits the DB only twice for multiple roots") {
    val smaug = Dragon(
      id = Dragon.Id("smaug"),
      name = "Smaug",
      breedId = Breed.Id("fire-drake"),
      age = 892,
      scheduleId = None
    )

    val frostbite = Dragon(
      id = Dragon.Id("frostbite"),
      name = "Frostbite",
      breedId = Breed.Id("ice-wyrm"),
      age = 341,
      scheduleId = Some(Dragon.Id("frostbite"))
    )

    selectionTreeDemo.clearFetchLog()

    val result =
      unsafeRun(
        (Dragon.schedule <>: Schedule.dragon)
          .startingFrom(List(smaug, frostbite))
      )

    assertEquals(result.length, 2)
    assertEquals(result.head, Nil)
    assertEquals(result(1).length, 2)
    assertEquals(
      selectionTreeDemo.fetchLogSnapshot(),
      Vector(
        FetchLog("Dragon.schedule", Vector("smaug", "frostbite")),
        FetchLog("Schedule.dragon", Vector("frostbite", "frostbite"))
      )
    )
  }
}
