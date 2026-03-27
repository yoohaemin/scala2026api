import decrel.Relation

case class Breed(
    id: Breed.Id,
    name: String,
    fireResistance: Int,
    maxAltitude: Int
)

object Breed {
  case class Id(value: String)

  case object dragons extends Relation.Many[Breed, List, Dragon]
}

case class Schedule(
    dragonId: Dragon.Id,
    startsAt: String,
    endsAt: String
)

object Schedule {
  case object dragon extends Relation.Single[Schedule, Dragon]
}

case class Dragon(
    id: Dragon.Id,
    name: String,
    breedId: Breed.Id,
    age: Int,
    scheduleId: Option[Dragon.Id]
)

object Dragon {
  case class Id(value: String)

  case object breed extends Relation.Single[Dragon, Breed]
  case object schedule extends Relation.Many[Dragon, List, Schedule]
  case object rentals extends Relation.Many[Dragon, List, Rental]
}

case class Rental(
    id: Rental.Id,
    dragonId: Dragon.Id
)

object Rental {
  case class Id(value: String)

  case object fetch extends Relation.Single[Rental.Id, Rental]
  case object dragon extends Relation.Single[Rental, Dragon]
}
