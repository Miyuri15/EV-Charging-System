// --------------------------------------------------------------
// File Name: Booking.cs
// Author: Miyuri Lokuhewage
// Description: Database entity representing booking details.
// Updated to include TimeSlot reference and SlotNumber.
// Created/Updated On: 09/10/2025
// --------------------------------------------------------------

using System;
using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace EvBackend.Entities
{
    public class Booking
    {
        [BsonId]
        [BsonRepresentation(BsonType.ObjectId)]
        public string BookingId { get; set; }

        public string StationId { get; set; }
        public string SlotId { get; set; }
        public int SlotNumber { get; set; }  // human-friendly
        public string TimeSlotId { get; set; }

        public string OwnerId { get; set; }  // NIC
        public string Status { get; set; } = "Pending"; // Pending, Approved, Charging, Finalized, Cancelled

        // Always UTC in DB
        public DateTime StartTime { get; set; }
        public DateTime EndTime { get; set; }

        // QR
        public string? QrCode { get; set; }
        public DateTime? QrExpiresAt { get; set; }
        public string? QrImageBase64 { get; set; }

        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
        public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

        public string? CancellationReason { get; set; }   // e.g. "Slot set to Out Of Order"
        public string? CancelledBy { get; set; }   
        
         [BsonElement("IsExpired")]
        public bool IsExpired { get; set; } = false;
        
        [BsonElement("ExpiredAt")]
        public DateTime? ExpiredAt { get; set; }
    }
}