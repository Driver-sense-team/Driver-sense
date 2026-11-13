package kkam.server.event

import org.springframework.data.jpa.repository.JpaRepository

interface DriveEventRepository : JpaRepository<DriveEvent, Long> {
    fun findByTripId(tripId: String): List<DriveEvent>
}