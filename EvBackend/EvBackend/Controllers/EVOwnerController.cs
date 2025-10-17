// --------------------------------------------------------------
// File Name: EVOwnerController.cs
// Author: Oshadi Jayananda
// Description: Handles EV Owner registration logic for the system
// Created On: 25/09/2025
// --------------------------------------------------------------

using EvBackend.Models.DTOs;
using EvBackend.Services.Interfaces;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Security.Authentication;
using System.Security.Claims;

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/owners")]
    public class EVOwnerController : ControllerBase
    {
        private readonly IEVOwnerService _evOwnerService;
        private readonly INotificationService _notificationService;

        public EVOwnerController(IEVOwnerService evOwnerService, INotificationService notificationService)
        {
            _evOwnerService = evOwnerService;
            _notificationService = notificationService;
        }

        //get all owners, only for admin
        [HttpGet]
        [Authorize(Roles = "Admin")]
        public async Task<IActionResult> GetAllOwners([FromQuery] int page = 1, [FromQuery] int pageSize = 10)
        {
            var owners = await _evOwnerService.GetAllEVOwners(page, pageSize);
            return Ok(owners);
        }

        //register new owner
        [HttpPost("register")]
        public async Task<IActionResult> RegisterOwner([FromBody] CreateEVOwnerDto createEVOwnerDto)
        {
            if (!ModelState.IsValid)
            {
                return BadRequest(new { message = "Invalid owner registration data." });
            }
            try
            {
                var response = await _evOwnerService.CreateEVOwner(createEVOwnerDto);
                return Ok(response);
            }
            catch (AuthenticationException ex)
            {
                return Unauthorized(new { message = ex.Message });
            }
            catch (ArgumentException ex)
            {
                return BadRequest(new { message = ex.Message });
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                return StatusCode(500, new { message = "An unexpected error occurred." });
            }
        }

        //update owner details
        [HttpPut("{nic}")]
        [Authorize(Roles = "Owner")]
        public async Task<IActionResult> UpdateOwner(string nic, [FromBody] UpdateEVOwnerDto dto)
        {
            if (!ModelState.IsValid) return BadRequest(new { message = "Invalid data." });

            var userNic = User.FindFirstValue(ClaimTypes.NameIdentifier);
            if (!string.Equals(userNic, nic, StringComparison.OrdinalIgnoreCase))
                return Forbid();

            try
            {
                var updated = await _evOwnerService.UpdateEVOwner(nic, dto);
                return Ok(updated);
            }
            catch (KeyNotFoundException)
            {
                return NotFound(new { message = "EV Owner not found." });
            }
        }

        // get owner by nic
        [HttpGet("{nic}")]
        [Authorize(Roles = "Owner,Admin")]
        public async Task<IActionResult> GetOwner(string nic)
        {
            var isOwner = User.IsInRole("Owner");
            if (isOwner)
            {
                var userNic = User.FindFirstValue(ClaimTypes.NameIdentifier);
                if (!string.Equals(userNic, nic, StringComparison.OrdinalIgnoreCase))
                    return Forbid();
            }

            try
            {
                var dto = await _evOwnerService.GetEVOwnerByNIC(nic);
                return Ok(dto);
            }
            catch (KeyNotFoundException)
            {
                return NotFound(new { message = "EV Owner not found." });
            }
        }

        // Deactivate self(owner)
        [HttpPatch("{nic}/deactivate")]
        [Authorize(Roles = "Owner")]
        public async Task<IActionResult> DeactivateSelf(string nic, [FromServices] IBookingService _bookingService)
        {
            var userNic = User.FindFirstValue(ClaimTypes.NameIdentifier);
            if (!string.Equals(userNic, nic, StringComparison.OrdinalIgnoreCase))
                return Forbid();

            try
            {
                // First clear any existing reactivation request when user deactivates themselves
                await _evOwnerService.ClearReactivationRequest(nic);
                var ok = await _evOwnerService.ChangeEVOwnerStatus(nic, false, _bookingService);
                if (!ok) return NotFound(new { message = "EV Owner not found." });
                return Ok(new { message = "Account deactivated." });
            }
            catch (InvalidOperationException ex)
            {
                return BadRequest(new { message = ex.Message });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { message = "Failed to deactivate account", error = ex.Message });
            }
        }

        // Activate by backoffice (admin)
        [HttpPatch("{nic}/activate")]
        [Authorize(Roles = "Admin")]
        public async Task<IActionResult> ActivateByBackoffice(string nic, [FromServices] IBookingService _bookingService)
        {
            try
            {
                var owner = await _evOwnerService.GetEVOwnerByNIC(nic);
                if (owner == null)
                    return NotFound(new { message = "EV Owner not found." });

                if (owner.IsActive)
                    return BadRequest(new { message = "Account is already active." });

                var ok = await _evOwnerService.ChangeEVOwnerStatus(nic, true, _bookingService);
                if (!ok)
                    return BadRequest(new { message = "Failed to activate account." });

                return Ok(new { message = $"EV Owner {owner.FullName} activated successfully." });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { message = "Unexpected server error", error = ex.Message });
            }
        }

        // Request reactivation by owner
        [HttpPatch("{nic}/request-reactivation")]
        [Authorize(Roles = "Owner")]
        public async Task<IActionResult> RequestReactivation(string nic)
        {
            var userNic = User.FindFirstValue(ClaimTypes.NameIdentifier);
            if (!string.Equals(userNic, nic, StringComparison.OrdinalIgnoreCase))
                return Forbid();

            try
            {
                var ok = await _evOwnerService.RequestReactivation(nic);
                if (!ok) return BadRequest(new { message = "Could not request reactivation." });
                return Ok(new { message = "Reactivation request submitted successfully." });
            }
            catch (KeyNotFoundException)
            {
                return NotFound(new { message = "EV Owner not found." });
            }
            catch (InvalidOperationException ex)
            {
                return BadRequest(new { message = ex.Message });
            }
        }

        // Get count of reactivation requests, only for admin
        [HttpGet("reactivation-count")]
        [Authorize(Roles = "Admin")]
        public async Task<IActionResult> GetReactivationRequestCount()
        {
            try
            {
                var count = await _evOwnerService.GetReactivationRequestCount();
                return Ok(new { count });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { message = "Failed to fetch reactivation count", error = ex.Message });
            }
        }


        // Get list of reactivation requests, only for admin
        [HttpGet("reactivation-requests")]
        [Authorize(Roles = "Admin")]
        public async Task<IActionResult> GetReactivationRequests()
        {
            try
            {
                var requests = await _evOwnerService.GetEVOwnersWithReactivationRequests();
                return Ok(requests);
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { message = "Failed to fetch reactivation requests", error = ex.Message });
            }
        }

        // Clear reactivation request by admin
        [HttpPatch("{nic}/clear-reactivation")]
        [Authorize(Roles = "Admin")]
        public async Task<IActionResult> ClearReactivationRequest(string nic)
        {
            try
            {
                var ok = await _evOwnerService.ClearReactivationRequest(nic);
                if (!ok) return NotFound(new { message = "EV Owner not found or no reactivation request to clear." });
                return Ok(new { message = "Reactivation request cleared successfully." });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { message = "Failed to clear reactivation request", error = ex.Message });
            }
        }
    }
}