// --------------------------------------------------------------
// File Name: StationController.cs
// Author: Denuwan Sathsara
// Description: Unified controller for station and slot management.
// Includes station CRUD, slot CRUD, and slot availability updates.
// Created/Updated On: 27/09/2025
// --------------------------------------------------------------

using System;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;
using EvBackend.Services.Interfaces;
using EvBackend.Models.DTOs;

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class StationController : ControllerBase
    {
        private readonly IStationService _stationService;

        public StationController(IStationService stationService)
        {
            _stationService = stationService;
        }

        // ---------------------------
        // 🚗 Station Endpoints
        // ---------------------------

        // Create station - Admin or Backoffice only
        [HttpPost]
        //[Authorize(Roles = "Admin")]
        public async Task<IActionResult> CreateStation([FromBody] CreateStationDto dto)
        {
            if (string.IsNullOrWhiteSpace(dto.Name) || string.IsNullOrWhiteSpace(dto.Location))
                return BadRequest(new { message = "Name and Location are required" });
            if (dto.Capacity <= 0) return BadRequest(new { message = "Capacity must be > 0" });

            try
            {
                var created = await _stationService.CreateStationAsync(dto);
                return CreatedAtAction(nameof(GetStationById), new { stationId = created.StationId }, created);
            }
            catch (ArgumentException ex) { return Conflict(new { message = ex.Message }); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        // Update station
        [HttpPut("{stationId}")]
        [Authorize(Roles = "Admin")]
        public async Task<IActionResult> UpdateStation(string stationId, [FromBody] UpdateStationDto dto)
        {
            if (string.IsNullOrWhiteSpace(dto.Name) || string.IsNullOrWhiteSpace(dto.Location))
                return BadRequest(new { message = "Name and Location are required" });
            if (dto.Capacity <= 0) return BadRequest(new { message = "Capacity must be > 0" });

            try
            {
                var updated = await _stationService.UpdateStationAsync(stationId, dto);
                if (updated == null) return NotFound(new { message = "Station not found" });
                return Ok(updated);
            }
            catch (ArgumentException ex) { return Conflict(new { message = ex.Message }); }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpPatch("{stationId}/toggle-status")]
        //[Authorize(Roles = "Admin")]
        public async Task<IActionResult> ToggleStationStatus(string stationId)
        {
            try
            {
                var success = await _stationService.ToggleStationStatusAsync(stationId);
                if (!success)
                    return BadRequest(new { message = "Cannot toggle station status (may have active bookings or some other issue)" });

                return Ok(new { message = "Station status updated successfully" });
            }
            catch (InvalidOperationException ex)
            {
                // Business rule: active bookings prevent deactivation or other validation failures
                return BadRequest(new { message = ex.Message });
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                return StatusCode(500, new { message = "Unexpected error" });
            }
        }


        // Get station by id
        [HttpGet("{stationId}")]
        [Authorize(Roles = "Admin,Operator")]
        public async Task<IActionResult> GetStationById(string stationId)
        {
            try
            {
                var dto = await _stationService.GetStationByIdAsync(stationId);
                if (dto == null) return NotFound(new { message = "Station not found" });
                return Ok(dto);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        // Get all stations
        [HttpGet]
        [Authorize]
        public async Task<IActionResult> GetAllStations([FromQuery] bool onlyActive = false, [FromQuery] int page = 1, [FromQuery] int pageSize = 10)
        {
            try
            {
                // Pass page number and page size to service method
                var result = await _stationService.GetAllStationsAsync(onlyActive, page, pageSize);
                return Ok(result);
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                return StatusCode(500, new { message = "Unexpected error" });
            }
        }


        // Search stations
        [HttpGet("search")]
        [Authorize]
        public async Task<IActionResult> SearchStations([FromQuery] string? type = null, [FromQuery] string? location = null)
        {
            try
            {
                var results = await _stationService.SearchStationsAsync(type, location);
                return Ok(results);
            }
            catch (Exception ex) { Console.WriteLine(ex); return StatusCode(500, new { message = "Unexpected error" }); }
        }

        [HttpGet("names")]
        [Authorize]
        public async Task<IActionResult> GetStationNames([FromQuery] string? type = null, [FromQuery] string? location = null)
        {
            try
            {
                var results = await _stationService.GetStationNameSuggestionsAsync(type, location);
                return Ok(results);
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                return StatusCode(500, new { message = "Unexpected error occurred." });
            }
        }


        [HttpGet("nearby")]
        [Authorize] // Optional: can restrict to logged-in users
        public async Task<IActionResult> GetNearbyStations([FromQuery] double latitude, [FromQuery] double longitude, [FromQuery] double radiusKm = 5)
        {
            try
            {
                var result = await _stationService.GetNearbyStationsAsync(latitude, longitude, radiusKm);
                return Ok(result);
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                return StatusCode(500, new { message = "Unexpected error" });
            }
        }

        [HttpDelete("{stationId}")]
        [Authorize(Roles = "Admin,Backoffice")]
        public async Task<IActionResult> DeleteStation(string stationId)
        {
            try
            {
                var ok = await _stationService.DeleteStationWithRelationsAsync(stationId);
                if (!ok) return NotFound(new { message = "Station not found" });
                return Ok(new { message = "Station and related data deleted successfully" });
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex);
                return StatusCode(500, new { message = "Error deleting station" });
            }
        }

    }
}
