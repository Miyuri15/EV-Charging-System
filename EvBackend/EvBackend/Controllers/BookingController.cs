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

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/bookings")]
    public class BookingController : ControllerBase
    {
        private readonly IBookingService _booking;

        public BookingController(IBookingService bookingService)
        {
            _booking = bookingService;
        }

        // ---------------------------
        // Availability for UI
        // ---------------------------

        // GET: /api/bookings/stations/{stationId}/timeslots?date=YYYY-MM-DD
        [HttpGet("stations/{stationId}/timeslots")]
        //[Authorize] // enable if needed
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
        //[Authorize]
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
        [Authorize(Roles = "Owner,Admin,Backoffice")]
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
        [Authorize(Roles = "Owner,Admin,Backoffice")]
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
        [Authorize(Roles = "Owner,Admin,Backoffice")]
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
        [Authorize(Roles = "Owner,Admin,Backoffice")]
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
        [Authorize(Roles = "Admin,Backoffice,Operator")]
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
        [Authorize(Roles = "Operator,Admin,Backoffice")]
        public async Task<IActionResult> GetTodayBookingsByStation(string stationId)
        {
            try
            {
                var list = await _booking.GetTodayApprovedBookingsAsync(stationId);
                return Ok(list);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("station/{stationId}/upcoming")]
        [Authorize(Roles = "Operator,Admin,Backoffice")]
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
        [Authorize(Roles = "Operator,Admin,Backoffice")]
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
        [Authorize(Roles = "Operator,Admin,Backoffice")]
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
        [Authorize(Roles = "Operator,Admin,Backoffice")]
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
        //[Authorize(Roles = "Admin,Backoffice,Operator")]
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
        //[Authorize(Roles = "Admin,Backoffice,Operator")]
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
        //[Authorize(Roles = "Admin")]
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
    }
}
