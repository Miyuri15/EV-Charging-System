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
using EvBackend.Services.Interfaces;

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/slots")]
    public class SlotController : ControllerBase
    {
        private readonly IMongoDatabase _db;
        private readonly IBookingService _bookingService;


        public SlotController(IMongoDatabase db, IBookingService bookingService)
        {
            _db = db;
            _bookingService = bookingService;
        }

        [HttpGet("station/{stationId}")]
        [Authorize]
        public async Task<IActionResult> GetSlotsByStation(string stationId)
        {
            var slots = _db.GetCollection<Slot>("Slots");
            var list = await slots.Find(s => s.StationId == stationId).ToListAsync();

            return Ok(list.Select(s => new
            {
                s.SlotId,
                s.StationId,
                s.Number,
                //s.ConnectorType,
                s.Status,
                s.CreatedAt,
                s.UpdatedAt
            }));
        }

        [HttpPatch("{slotId}/toggle")]
        [Authorize(Roles = "Operator,Admin,Backoffice")]
        public async Task<IActionResult> ToggleSlotStatus(string slotId)
        {
            var slots = _db.GetCollection<Slot>("Slots");
            var bookings = _db.GetCollection<Booking>("Bookings");

            var slot = await slots.Find(s => s.SlotId == slotId).FirstOrDefaultAsync();
            if (slot == null)
                return NotFound(new { message = "Slot not found" });

            var activeBooking = await bookings.Find(b =>
                b.SlotId == slotId &&
                (b.Status == "Pending" || b.Status == "Approved" || b.Status == "Charging")
            ).FirstOrDefaultAsync();

            if (activeBooking != null)
                return Conflict(new { message = "Cannot toggle slot linked to an active booking" });

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


        [HttpPatch("{slotId}/status")]
        [Authorize(Roles = "Operator,Admin,Backoffice")]
        public async Task<IActionResult> UpdateSlotStatus(string slotId, [FromBody] SlotStatusUpdateDto dto)
        {
            if (string.IsNullOrWhiteSpace(dto.Status))
                return BadRequest(new { message = "Status is required" });

            var normalized = dto.Status.Trim();
            if (string.Equals(normalized, "Out of Order", StringComparison.OrdinalIgnoreCase))
                normalized = "Out Of Order";

            var validStatuses = new[] { "Available", "Under Maintenance", "Out Of Order" };
            if (!validStatuses.Contains(normalized))
                return BadRequest(new { message = "Invalid status" });

            var slots = _db.GetCollection<Slot>("Slots");
            var bookings = _db.GetCollection<Booking>("Bookings");
            var stations = _db.GetCollection<Station>("Stations"); // Add reference to the Station collection

            var slot = await slots.Find(s => s.SlotId == slotId).FirstOrDefaultAsync();
            if (slot == null)
                return NotFound(new { message = "Slot not found" });

            // Block if slot is currently charging
            var now = DateTime.UtcNow;
            var active = await bookings.Find(b =>
                b.SlotId == slotId &&
                b.Status == "Charging" &&
                b.StartTime <= now && now < b.EndTime).AnyAsync();

            if (active)
                return Conflict(new { message = "Cannot change status while slot is actively charging" });

            // Update slot status
            await slots.UpdateOneAsync(
                s => s.SlotId == slotId,
                Builders<Slot>.Update
                    .Set(s => s.Status, normalized)
                    .Set(s => s.UpdatedAt, DateTime.UtcNow)
            );

            // Update the availableSlots in the associated Station
            var station = await stations.Find(s => s.StationId == slot.StationId).FirstOrDefaultAsync();
            if (station != null)
            {
                // If the status changes to Available, increment availableSlots
                // If the status changes from Available, decrement availableSlots
                int availableSlotsChange = (normalized == "Available" && slot.Status != "Available") ? 1 :
                    (slot.Status == "Available" && normalized != "Available") ? -1 : 0;

                if (availableSlotsChange != 0)
                {
                    var stationUpdate = Builders<Station>.Update
                        .Inc(s => s.AvailableSlots, availableSlotsChange)
                        .Set(s => s.UpdatedAt, DateTime.UtcNow);

                    await stations.UpdateOneAsync(s => s.StationId == slot.StationId, stationUpdate);
                }
            }

            // Auto-cancel future bookings if slot becomes unavailable
            int cancelled = 0;
            if (normalized is "Under Maintenance" or "Out Of Order")
            {
                cancelled = await _bookingService.AutoCancelFutureBookingsForSlotAsync(
                    slotId,
                    $"Auto-cancelled because slot set to '{normalized}'",
                    "System");
            }

            return Ok(new
            {
                message = $"Slot {slot.Number} set to {normalized}" +
                        (cancelled > 0 ? $"; auto-cancelled {cancelled} future booking(s)." : ""),
                status = normalized,
                slotId = slot.SlotId,
                slotNumber = slot.Number
            });
        }




    }


    public class SlotStatusUpdateDto
    {
        public string Status { get; set; }
    }
}
