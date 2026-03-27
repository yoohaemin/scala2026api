import zio.{Schedule => _, *}
import zio.Console.printLine

object Main extends ZIOAppDefault {

  def run = {
    for {
      repo <- DemoData.repo
      errorSchemaDemo = new ErrorSchemaDemo(repo)
      matchExample = new MatchExample(errorSchemaDemo)
      graphQLApiDemo = new GraphQLApiDemo(errorSchemaDemo)
      selectionTreeProofs = new SelectionTreeProofs(repo)
      selectionTreeDemo = new SelectionTreeDemo(selectionTreeProofs)
      smaug <- errorSchemaDemo.rentDragon(Dragon.Id("smaug")).either
      frostbite <- errorSchemaDemo.rentDragon(Dragon.Id("frostbite")).either
      missing <- errorSchemaDemo.rentDragon(Dragon.Id("missing")).either
      handled <- matchExample.handled
      _ <- printLine("Error schema demo")
      _ <- printLine(
        s"rentDragon(smaug) -> $smaug"
      )
      _ <- printLine(
        s"rentDragon(frostbite) -> $frostbite"
      )
      _ <- printLine(
        s"rentDragon(missing) -> $missing"
      )
      _ <- printLine(s"match example -> $handled")
      _ <- printLine("graphql schema ->")
      _ <- printLine(graphQLApiDemo.renderedSchema)
      _ <- printLine("")
      _ <- printLine("Selection tree demo")
      _ <- ZIO.succeed(selectionTreeDemo.clearFetchLog())
      full <- selectionTreeDemo.fullResult
      _ <- printLine(s"full -> $full")
      _ <- printLine(s"full fetch log -> ${selectionTreeDemo.fetchLogSnapshot()}")
      _ <- ZIO.succeed(selectionTreeDemo.clearFetchLog())
      reduced <- selectionTreeDemo.reducedResult
      _ <- printLine(s"reduced -> $reduced")
      _ <- printLine(s"reduced fetch log -> ${selectionTreeDemo.fetchLogSnapshot()}")
      _ <- ZIO.succeed(selectionTreeDemo.clearFetchLog())
      deduplicated <- selectionTreeDemo.deduplicatedDragonResult
      _ <- printLine(s"deduplicated -> $deduplicated")
      _ <- printLine(
        s"deduplicated fetch log -> ${selectionTreeDemo.fetchLogSnapshot()}"
      )
      _ <- ZIO.succeed(selectionTreeDemo.clearFetchLog())
      batched <- selectionTreeDemo.batchedBreedResults
      _ <- printLine(s"batched -> $batched")
      _ <- printLine(s"batched fetch log -> ${selectionTreeDemo.fetchLogSnapshot()}")
    } yield ()
  }
}
