package kkam.server.event

import jakarta.persistence.*
import kkam.engine.model.Result
import kkam.engine.model.RuleId
import kkam.engine.model.TurnType
import java.time.Instant

@Entity
@Table(
    name = "drive_event",
    uniqueConstraints = [UniqueConstraint(columnNames = ["trip_id", "event_id"])]
)
class DriveEvent(
    @Column(name = "trip_id", nullable = false)
    val tripId: String,

    @Column(name = "event_id", nullable = false)
    val eventId: String,

    val t: Long,

    @Enumerated(EnumType.STRING)
    val rule: RuleId,

    @Enumerated(EnumType.STRING)
    val result: Result,

    val guided: Boolean,

    @Enumerated(EnumType.STRING)
    val turn: TurnType?,

    val lat: Double,
    val lon: Double,

    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
}