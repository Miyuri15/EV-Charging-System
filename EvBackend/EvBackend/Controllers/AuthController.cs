﻿// --------------------------------------------------------------
// File Name: AuthController.cs
// Author: Hasindu Koshitha
// Description: Handles authentication logic for the system
// Created On: 13/09/2025
// --------------------------------------------------------------

using EvBackend.Models.DTOs;
using EvBackend.Services.Interfaces;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using System.Security.Authentication;
using System.Security.Claims;

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/auth")]
    public class AuthController : ControllerBase
    {
        private readonly IAuthService _authService;
        private readonly IUserService _userService;
        private readonly IEVOwnerService _evOwnerService;

        public AuthController(IAuthService authService, IUserService userService, IEVOwnerService evOwnerService)
        {
            _authService = authService;
            _userService = userService;
            _evOwnerService = evOwnerService;
        }

        [HttpPost("login")]
        public async Task<IActionResult> Login([FromBody] LoginDto loginDto)
        {
            try
            {
                var response = await _authService.AuthenticateUser(loginDto);
                return Ok(new
                {
                    token = response.Token
                });
            }
            catch (AuthenticationException ex)
            {
                return Unauthorized(new { message = ex.Message });
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                return StatusCode(500, new { message = "An unexpected error occurred." });
            }
        }

        // Verifies token validity and returns current user information
        [HttpGet("me")]
        [Authorize]
        public async Task<IActionResult> Me()
        {
            var role = User.Claims.FirstOrDefault(c => c.Type == ClaimTypes.Role)?.Value;
            var userId = User.Claims.FirstOrDefault(c => c.Type == System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

            var nic = "";

            if (role == "Owner") {
                nic = userId;
                userId = "";
            }

            if(role == null)
                return Unauthorized(new { message = "Invalid token" });

            if (string.IsNullOrEmpty(userId) && string.IsNullOrEmpty(nic))
                return Unauthorized(new { message = "Invalid token" });

            // Check if EVOwner by NIC
            if (!string.IsNullOrEmpty(nic) && role == "Owner")
            {
                var owner = await _evOwnerService.GetEVOwnerByNIC(nic);
                if (owner != null)
                {
                    return Ok(new EVOwnerDto
                    {
                        NIC = owner.NIC,
                        FullName = owner.FullName,
                        Email = owner.Email,
                        IsActive = owner.IsActive,
                        CreatedAt = owner.CreatedAt
                    });
                }
            }

            // Otherwise, fallback to normal User
            var user = await _userService.GetUserById(userId);
            if (user != null)
            {
                return Ok(new UserDto
                {
                    Id = user.Id,
                    FullName = user.FullName,
                    Email = user.Email,
                    Role = user.Role,
                    IsActive = user.IsActive
                });
            }

            return NotFound(new { message = "User not found" });
        }

        [HttpPost("logout")]
        [Authorize]
        public IActionResult Logout()
        {
            return Ok(new { message = "Logged out successfully" });
        }

        [HttpPost("reset-password")]
        public async Task<IActionResult> ResetPassword([FromBody] ResetPasswordDto resetPasswordDto)
        {
            try
            {
                await _authService.ResetPassword(resetPasswordDto);
                return Ok(new { message = "Password has been reset successfully" });
            }
            catch (AuthenticationException ex)
            {
                return Unauthorized(new { message = ex.Message });
            }
            catch (ArgumentException ex)
            {
                return BadRequest(new { message = ex.Message });
            }
            catch (KeyNotFoundException ex)
            {
                return NotFound(new { message = ex.Message });
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                return StatusCode(500, new { message = "An unexpected error occurred." });
            }
        }

        [HttpPost("forgot-password")]
        public async Task<IActionResult> ForgotPassword([FromBody] ForgotPasswordDto forgotPasswordDto)
        {
            try
            {
                await _authService.SendPasswordResetEmail(forgotPasswordDto);
                return Ok(new { message = "If an account with that email exists, a password reset link has been sent." });
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
    }
}
