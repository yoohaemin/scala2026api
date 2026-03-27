import decrel.*
import zio.Task

final class SelectionTreeDemo(
    val repo: H2DemoRepo,
    val selectionTreeProofs: SelectionTreeProofs
) {
  import selectionTreeProofs.{*, given}

  def this(selectionTreeProofs: SelectionTreeProofs) =
    this(selectionTreeProofs.repo, selectionTreeProofs)

  val rentalId = Rental.Id("rental-2")
  val allRentalIds = List(Rental.Id("rental-1"), Rental.Id("rental-2"))

  def fetchLogSnapshot(): Vector[FetchLog] =
    repo.fetchLogSnapshot()

  def clearFetchLog(): Unit =
    repo.clearFetchLog()

  val fullResult =
    // use .expand on the value
    rentalId.expand(
      Rental.fetch >>: Rental.dragon <>: (Dragon.breed & Dragon.schedule)
    )

  val reducedResult: Task[(Dragon, Breed)] =
    (Rental.fetch >>: Rental.dragon <>: Dragon.breed)
      // or use .startingFrom on the relation
      .startingFrom(rentalId)

  val deduplicatedDragonResult: Task[(Dragon, Dragon)] =
    (Rental.fetch >>: (Rental.dragon & Rental.dragon))
      .startingFrom(rentalId)

  // Batch calls use the same method
  val batchedBreedResults: Task[List[(Dragon, Breed)]] =
    (Rental.fetch >>: Rental.dragon <>: Dragon.breed)
      .startingFrom(allRentalIds)

  // Schedule.dragon: go from rental -> dragon -> schedules -> dragon (of each schedule)
  val schedulesWithDragon =
    (Rental.fetch <>: Rental.dragon <>: Dragon.schedule <>: Schedule.dragon <>: Dragon.schedule <>: Schedule.dragon)
      .startingFrom(allRentalIds)

  val allRentalsForRental =
    (Rental.fetch <>: Rental.dragon <>: Dragon.rentals <>: Rental.dragon)
      .startingFrom(allRentalIds)
}
