// --------------------------------------------------------------
// File Name: SlotController.cs
// Author: Miyuri Lokuhewage
// Description: Operator slot availability controller
// --------------------------------------------------------------

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;
using EvBackend.Entities;
using System;
using System.Threading.Tasks;

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/slots")]
    public class SlotController : ControllerBase
    {
        private readonly IMongoDatabase _db;

        public SlotController(IMongoDatabase db)
        {
            _db = db;
        }

        // ✅ Get all slots of a station
        [HttpGet("station/{stationId}")]
        [Authorize(Roles = "Operator,Admin,Backoffice")]
        public async Task<IActionResult> GetSlotsByStation(string stationId)
        {
            var slots = _db.GetCollection<Slot>("Slots");
            var list = await slots.Find(s => s.StationId == stationId).ToListAsync();

            return Ok(list.Select(s => new
            {
                s.SlotId,
                s.StationId,
                s.Number,
                s.ConnectorType,
                s.Status,
                s.CreatedAt,
                s.UpdatedAt
            }));
        }

        // ✅ Toggle between Available ↔ Booked
        [HttpPatch("{slotId}/toggle")]
        [Authorize(Roles = "Operator,Admin,Backoffice")]
        public async Task<IActionResult> ToggleSlotStatus(string slotId)
        {
            var slots = _db.GetCollection<Slot>("Slots");
            var bookings = _db.GetCollection<Booking>("Bookings");

            var slot = await slots.Find(s => s.SlotId == slotId).FirstOrDefaultAsync();
            if (slot == null)
                return NotFound(new { message = "Slot not found" });

            // 🚫 Prevent toggling if slot is part of active booking
            var activeBooking = await bookings.Find(b =>
                b.SlotId == slotId &&
                (b.Status == "Pending" || b.Status == "Approved" || b.Status == "Charging")
            ).FirstOrDefaultAsync();

            if (activeBooking != null)
                return Conflict(new { message = "Cannot toggle slot linked to an active booking" });

            // 🔁 Toggle status
            string newStatus = slot.Status == "Available" ? "Booked" : "Available";
            var update = Builders<Slot>.Update
                .Set(s => s.Status, newStatus)
                .Set(s => s.UpdatedAt, DateTime.UtcNow);

            await slots.UpdateOneAsync(s => s.SlotId == slotId, update);

            return Ok(new
            {
                message = $"Slot {slot.Number} marked as {newStatus}",
                newStatus
            });
        }
    }
}
