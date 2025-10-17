// --------------------------------------------------------------
// File Name: BookingDto.cs
// Author: Miyuri Lokuhewage
// Description: DTOs for booking management aligned to the new flow.
// Created/Updated On: 09/10/2025
// --------------------------------------------------------------

using System;

namespace EvBackend.Models.DTOs
{
    public class BookingDto
    {
        public string BookingId { get; set; }
        public string StationId { get; set; }
        public string StationName { get; set; }

        public string SlotId { get; set; }
        public int SlotNumber { get; set; }

        public string TimeSlotId { get; set; }
        public string OwnerId { get; set; }
        public string Status { get; set; } // Pending, Approved, Charging, Finalized, Cancelled

        // UTC in DB
        public DateTime StartTime { get; set; }
        public DateTime EndTime { get; set; }

        public string? QrCode { get; set; }
        public DateTime? QrExpiresAt { get; set; }
        public string? QrImageBase64 { get; set; }

        public DateTime CreatedAt { get; set; }
        public DateTime UpdatedAt { get; set; }

        // For UI (Sri Lanka 24h formatting)
        public string? FormattedStartTime { get; set; } // "yyyy MMM dd, HH:mm"
        public string? FormattedEndTime { get; set; }   // "yyyy MMM dd, HH:mm"
        public string? FormattedDate { get; set; }      // "yyyy MMM dd"

        public string? StationLocation { get; set; }
        public string? SlotName { get; set; } // e.g. "Slot 1"
        public string? TimeSlotRange { get; set; } // e.g. "08:00 - 10:00"
        public string? OwnerName { get; set; }
        public string? CancellationReason { get; set; }

        public bool IsExpired { get; set; }
        public DateTime? ExpiredAt { get; set; }
    }

    // Create = StationId + TimeSlotId + SlotId
    public class CreateBookingDto
    {
        public string StationId { get; set; }
        public string TimeSlotId { get; set; }
        public string SlotId { get; set; }
    }

    // Update (reschedule) = move to another timeslot+slot
    public class UpdateBookingDto
    {
        public string NewTimeSlotId { get; set; }
        public string NewSlotId { get; set; }
    }
}
