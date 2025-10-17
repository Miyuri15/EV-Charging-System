// --------------------------------------------------------------
// File Name: BookingController.cs
// Author: Miyuri Lokuhewage
// Description: Booking endpoints for new flow (station→timeslot→slot).
// Enforces 12h rules, returns SL formatted fields for UI.
// Created/Updated On: 09/10/2025
// --------------------------------------------------------------

using System;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using EvBackend.Services.Interfaces;
using EvBackend.Models.DTOs;
using MongoDB.Driver;
using EvBackend.Entities;


namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/bookings")]
    public class BookingController : ControllerBase
    {
        private readonly IBookingService _booking;
        private readonly IMongoDatabase _db;

        public BookingController(IBookingService bookingService,IMongoDatabase db)
        {
            _booking = bookingService;
            _db = db;
        }

        // ---------------------------
        // Availability for UI
        // ---------------------------

        // GET: /api/bookings/stations/{stationId}/timeslots?date=YYYY-MM-DD
        [HttpGet("stations/{stationId}/timeslots")]
        [Authorize]
        public async Task<IActionResult> GetAvailableTimeSlotsForStation(string stationId, [FromQuery] string date)
        {
            try
            {
                var list = await _booking.GetAvailableTimeSlotsForStationAsync(stationId, date);
                return Ok(list);
            }
            catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        // GET: /api/bookings/timeslots/{timeSlotId}/available-slots
        [HttpGet("timeslots/{timeSlotId}/available-slots")]
        [Authorize]
        public async Task<IActionResult> GetAvailableSlotsForTimeSlot(string timeSlotId)
        {
            try
            {
                var list = await _booking.GetAvailableSlotsForTimeSlotAsync(timeSlotId);
                return Ok(list);
            }
            catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        // ---------------------------
        // Owner: Create / Update / Cancel
        // ---------------------------

        [HttpPost]
        [Authorize(Roles = "Owner,Admin")]
        public async Task<IActionResult> CreateBooking([FromBody] CreateBookingDto dto)
        {
            if (dto == null) return BadRequest(new { message = "Booking data required" });
            if (string.IsNullOrWhiteSpace(dto.StationId) || string.IsNullOrWhiteSpace(dto.TimeSlotId) || string.IsNullOrWhiteSpace(dto.SlotId))
                return BadRequest(new { message = "StationId, TimeSlotId and SlotId are required" });

            var ownerId = User?.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
            if (string.IsNullOrWhiteSpace(ownerId))
                return Unauthorized(new { message = "OwnerId required" });

            try
            {
                var created = await _booking.CreateBookingAsync(dto, ownerId);
                return CreatedAtAction(nameof(GetBookingById), new { bookingId = created.BookingId }, created);
            }
            catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
            catch (InvalidOperationException ex) { return Conflict(new { message = ex.Message }); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpPut("{bookingId}")]
        [Authorize(Roles = "Owner,Admin")]
        public async Task<IActionResult> UpdateBooking(string bookingId, [FromBody] UpdateBookingDto dto)
        {
            var requesterId = User?.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
            var role = User?.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value;

            try
            {
                var updated = await _booking.UpdateBookingAsync(bookingId, dto, requesterId, role);
                if (updated == null) return NotFound(new { message = "Booking not found" });
                return Ok(updated);
            }
            catch (UnauthorizedAccessException) { return Forbid(); }
            catch (InvalidOperationException ex) { return Conflict(new { message = ex.Message }); }
            catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpPatch("{bookingId}/cancel")]
        [Authorize(Roles = "Owner,Admin")]
        public async Task<IActionResult> CancelBooking(string bookingId)
        {
            var requesterId = User?.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
            var role = User?.FindFirst(System.Security.Claims.ClaimTypes.Role)?.Value;

            try
            {
                var ok = await _booking.CancelBookingAsync(bookingId, requesterId, role);
                if (!ok) return NotFound(new { message = "Booking not found" });
                return Ok(new { message = "Booking cancelled" });
            }
            catch (UnauthorizedAccessException) { return Forbid(); }
            catch (InvalidOperationException ex) { return Conflict(new { message = ex.Message }); }
            catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        // ---------------------------
        // Shared
        // ---------------------------

        [HttpGet("{bookingId}")]
        [Authorize]
        public async Task<IActionResult> GetBookingById(string bookingId)
        {
            try
            {
                var dto = await _booking.GetBookingByIdAsync(bookingId);
                if (dto == null) return NotFound(new { message = "Booking not found" });
                return Ok(dto);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("owner/{ownerId}")]
        [Authorize(Roles = "Owner,Admin")]
        public async Task<IActionResult> GetBookingsByOwner(string ownerId)
        {
            try
            {
                var list = await _booking.GetBookingsByOwnerAsync(ownerId);
                return Ok(list);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("station/{stationId}")]
        [Authorize(Roles = "Admin,Operator")]
        public async Task<IActionResult> GetBookingsByStation(string stationId)
        {
            try
            {
                var list = await _booking.GetBookingsByStationAsync(stationId);
                return Ok(list);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("station/{stationId}/today")]
        [Authorize(Roles = "Operator,Admin")]
        public async Task<IActionResult> GetTodayBookingsByStation(string stationId)
        {
            try
            {
                var list = await _booking.GetTodayApprovedBookingsAsync(stationId);
                return Ok(list);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("station/{stationId}/count")]
        [Authorize(Roles = "Operator,Admin")]
        public async Task<IActionResult> GetApprovedBookingCountByStation(string stationId)
        {
            try
            {
                var todayCount = await _booking.GetApprovedBookingCountByStationAsync(stationId, true);
                var futureCount = await _booking.GetApprovedBookingCountByStationAsync(stationId, false);
                return Ok(new { todayCount, futureCount });
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                return StatusCode(500, new { message = "Unexpected error" });
            }
        }

        [HttpGet("station/{stationId}/upcoming")]
        [Authorize(Roles = "Operator,Admin")]
        public async Task<IActionResult> GetUpcomingBookingsByStation(string stationId)
        {
            try
            {
                var list = await _booking.GetUpcomingApprovedBookingsAsync(stationId);
                return Ok(list);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        // ---------------------------
        // Operator/Admin
        // ---------------------------

        [HttpPatch("{bookingId}/approve")]
        [Authorize(Roles = "Operator,Admin")]
        public async Task<IActionResult> ApproveBooking(string bookingId)
        {
            var operatorId = User?.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

            try
            {
                var ok = await _booking.ApproveBookingAsync(bookingId, operatorId);
                if (!ok) return NotFound(new { message = "Booking not found or not pending" });
                return Ok(new { message = "Booking approved" });
            }
            catch (UnauthorizedAccessException) { return Forbid(); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpPatch("{bookingId}/start")]
        [Authorize(Roles = "Operator,Admin")]
        public async Task<IActionResult> StartCharging(string bookingId)
        {
            var operatorId = User?.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

            try
            {
                var ok = await _booking.StartChargingAsync(bookingId, operatorId);
                if (!ok) return NotFound(new { message = "Booking not found or invalid state" });
                return Ok(new { message = "Booking marked as Charging" });
            }
            catch (UnauthorizedAccessException) { return Forbid(); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpPatch("{bookingId}/finalize")]
        [Authorize(Roles = "Operator,Admin")]
        public async Task<IActionResult> FinalizeBooking(string bookingId)
        {
            var operatorId = User?.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

            try
            {
                var ok = await _booking.FinalizeBookingAsync(bookingId, operatorId);
                if (!ok) return NotFound(new { message = "Booking not found or invalid state" });
                return Ok(new { message = "Booking finalized" });
            }
            catch (UnauthorizedAccessException) { return Forbid(); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        // ---------------------------
        // QR + Counts
        // ---------------------------

        [HttpGet("{bookingId}/qrcode")]
        [Authorize]
        public async Task<IActionResult> GenerateQrCode(string bookingId)
        {
            try
            {
                var (base64, expiresAt) = await _booking.GenerateQrCodeAsync(bookingId);
                if (base64 == null)
                    return NotFound(new { message = "Booking not found or QR generation failed" });

                return Ok(new { qrcodeBase64 = base64, expiresAt });
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("count/pending")]
        [Authorize(Roles = "Admin,Operator")]
        public async Task<IActionResult> CountPendingBookings()
        {
            try
            {
                var count = await _booking.CountPendingBookingsAsync();
                return Ok(new { pendingCount = count });
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("count/approved")]
        [Authorize(Roles = "Admin,Operator")]
        public async Task<IActionResult> CountApprovedFutureBookings()
        {
            try
            {
                var count = await _booking.CountApprovedFutureBookingsAsync();
                return Ok(new { approvedCount = count });
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }


        [HttpGet("overview")]
        [Authorize(Roles = "Admin")]
        public async Task<IActionResult> GetReservationOverview([FromQuery] DateTime? fromDate, [FromQuery] DateTime? toDate)
        {
            try
            {
                var result = await _booking.GetReservationOverviewAsync(fromDate, toDate);
                return Ok(result);
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { message = "Unexpected error", details = ex.Message });
            }
        }

        [HttpGet("pending")]
        [Authorize(Roles = "Admin,Operator")]
        public async Task<IActionResult> GetPendingBookings([FromQuery] int pageNumber = 1, [FromQuery] int pageSize = 10)
        {
            if (pageNumber <= 0 || pageSize <= 0 || pageSize > 100)
                return BadRequest(new { message = "Invalid pagination parameters" });
            try
            {
                var list = await _booking.GetPendingBookingsAsync(pageNumber, pageSize);
                return Ok(list);
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" });
            }
        }

        [HttpGet("approved")]
        [Authorize(Roles = "Admin,Operator")]
        public async Task<IActionResult> GetApprovedBookings([FromQuery] int pageNumber = 1, [FromQuery] int pageSize = 10)
        {
            if (pageNumber <= 0 || pageSize <= 0 || pageSize > 100)
                return BadRequest(new { message = "Invalid pagination parameters" });
            try
            {
                var list = await _booking.GetApprovedBookingsAsync(pageNumber, pageSize);
                return Ok(list);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("completed")]
        [Authorize(Roles = "Admin,Operator")]
        public async Task<IActionResult> GetCompletedBookings([FromQuery] int pageNumber = 1, [FromQuery] int pageSize = 10)
        {
            if (pageNumber <= 0 || pageSize <= 0 || pageSize > 100)
                return BadRequest(new { message = "Invalid pagination parameters" });
            try
            {
                var list = await _booking.GetCompletedBookingsAsync(pageNumber, pageSize);
                return Ok(list);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

         // ---------------------------
        // Booking Expiration
        // ---------------------------


        // Add this method to your BookingController class
[HttpPost("force-expire-check")]
[Authorize(Roles = "Admin")]
public async Task<IActionResult> ForceExpireCheck()
{
    try
    {
        // We need to implement ExpireOldBookingsAsync in the service first
        // For now, let's create a simple version that does the same as the background service
        var bookingCol = _db.GetCollection<Booking>("Bookings");
        var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");
        
        var nowUtc = DateTime.UtcNow;
        
        // Find bookings that should be expired
        var filter = Builders<Booking>.Filter.And(
            Builders<Booking>.Filter.In(b => b.Status, new[] { "Pending", "Approved" }),
            Builders<Booking>.Filter.Lt(b => b.EndTime, nowUtc),
            Builders<Booking>.Filter.Eq(b => b.IsExpired, false)
        );

        var expiredBookings = await bookingCol.Find(filter).ToListAsync();
        var expiredCount = 0;

        foreach (var booking in expiredBookings)
        {
            using var session = await _db.Client.StartSessionAsync();
            session.StartTransaction();

            try
            {
                // Update booking status to expired
                var bookingUpdate = Builders<Booking>.Update
                    .Set(b => b.Status, "Expired")
                    .Set(b => b.IsExpired, true)
                    .Set(b => b.ExpiredAt, nowUtc)
                    .Set(b => b.UpdatedAt, nowUtc);

                await bookingCol.UpdateOneAsync(
                    session,
                    Builders<Booking>.Filter.Eq(b => b.BookingId, booking.BookingId),
                    bookingUpdate
                );

                // Free up the timeslot
                await timeSlotCol.UpdateOneAsync(
                    session,
                    Builders<TimeSlot>.Filter.Eq(t => t.TimeSlotId, booking.TimeSlotId),
                    Builders<TimeSlot>.Update.Set(t => t.Status, "Available")
                );

                await session.CommitTransactionAsync();
                expiredCount++;
            }
            catch (Exception ex)
            {
                await session.AbortTransactionAsync();
                Console.WriteLine($"Failed to expire booking {booking.BookingId}: {ex.Message}");
            }
        }

        return Ok(new { 
            message = $"Manual expiration check completed",
            expiredCount = expiredCount,
            timestamp = DateTime.UtcNow,
            sriLankaTime = ConvertUtcToSriLankaTime(DateTime.UtcNow).ToString("yyyy-MM-dd HH:mm:ss")
        });
    }
    catch (Exception ex)
    {
        Console.WriteLine(ex);
        return StatusCode(500, new { message = "Unexpected error while expiring bookings" });
    }
}


        private static DateTime ConvertUtcToSriLankaTime(DateTime utcTime)
        {
            try
            {
                var sriLankaTz = TimeZoneInfo.FindSystemTimeZoneById("Sri Lanka Standard Time");
                return TimeZoneInfo.ConvertTimeFromUtc(utcTime, sriLankaTz);
            }
            catch
            {
                try
                {
                    var sriLankaTz = TimeZoneInfo.FindSystemTimeZoneById("Asia/Colombo");
                    return TimeZoneInfo.ConvertTimeFromUtc(utcTime, sriLankaTz);
                }
                catch
                {
                    return utcTime.AddHours(5.5);
                }
            }
        }

    }
}
