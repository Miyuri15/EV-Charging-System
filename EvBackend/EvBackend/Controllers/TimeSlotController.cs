// --------------------------------------------------------------
// File Name: TimeSlotController.cs
// Author: Miyuri Lokuhewage
// Description: Administrative controller for managing and syncing
//              time slots in EV Backend. Includes manual trigger
//              for cleanup and regeneration.
// Created/Updated On: 09/10/2025
// --------------------------------------------------------------

using Microsoft.AspNetCore.Mvc;
using EvBackend.Services;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Authorization;

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    [Tags("Admin Tools")] // ✅ Will appear in Swagger under this section
    public class TimeSlotController : ControllerBase
    {
        private readonly TimeSlotSchedulerService _scheduler;

        public TimeSlotController(TimeSlotSchedulerService scheduler)
        {
            _scheduler = scheduler;
        }

        /// <summary>
        /// 🕛 Cleans up expired time slots and generates new ones for the next day (Admin only).
        /// </summary>
        /// <remarks>
        /// This endpoint:
        /// - Deletes all expired time slots (before today)
        /// - Creates new 10 fixed slots per station per slot for the next day
        /// - Keeps rolling 7-day slot availability
        /// 
        /// Example: Use when you want to manually refresh the schedule instead of waiting for the midnight job.
        /// </remarks>
        /// <response code="200">Time slots successfully cleaned and regenerated.</response>
        /// <response code="500">Internal server error occurred during execution.</response>
        [HttpPost("sync")]
        [Authorize(Roles = "Admin")] // ✅ Restrict access to Admins
        public async Task<IActionResult> SyncTimeSlots()
        {
            await _scheduler.CleanupAndGenerateNextDayAsync();
            return Ok(new
            {
                message = "✅ Time slot cleanup & generation completed successfully."
            });
        }


        [HttpGet]
        [Authorize]
        public async Task<IActionResult> GetTimeSlotsByDate(
            [FromQuery] string stationId,
            [FromQuery] string slotId,
            [FromQuery] DateTime date)
        {
            if (string.IsNullOrEmpty(stationId) || string.IsNullOrEmpty(slotId))
                return BadRequest(new { message = "stationId and slotId are required" });

            var results = await _scheduler.GetTimeSlotsByDateAsync(stationId, slotId, date);
            if (!results.Any())
                return NotFound(new { message = "No time slots found for the given date" });

            return Ok(results);
        }

    }
}
