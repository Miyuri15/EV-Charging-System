// --------------------------------------------------------------
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
        private readonly ILogger<AuthController> _logger;

        public AuthController(IAuthService authService, IUserService userService, IEVOwnerService evOwnerService, ILogger<AuthController> logger)
        {
            _authService = authService;
            _userService = userService;
            _evOwnerService = evOwnerService;
            _logger = logger;
        }

        //login
        [HttpPost("login")]
        public async Task<IActionResult> Login([FromBody] LoginDto loginDto)
        {
            try
            {
                var response = await _authService.AuthenticateUser(loginDto, HttpContext.Request);
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
                _logger.LogError(ex, "An unexpected error occurred during login.");
                return StatusCode(500, new { message = "An unexpected error occurred." });
            }
        }

        //get logged in user details
        // [HttpGet("me")]
        // [Authorize]
        // public async Task<IActionResult> Me()
        // {
        //     try
        //     {
        //         var role = User.Claims.FirstOrDefault(c => c.Type == ClaimTypes.Role)?.Value;
        //         var userId = User.Claims.FirstOrDefault(c => c.Type == ClaimTypes.NameIdentifier)?.Value;

        //         var nic = "";

        //         if (role == "Owner")
        //         {
        //             nic = userId;
        //             userId = "";
        //         }

        //         if (role == null)
        //             return Unauthorized(new { message = "Invalid token" });

        //         if (string.IsNullOrEmpty(userId) && string.IsNullOrEmpty(nic))
        //             return Unauthorized(new { message = "Invalid token" });

        //         // Check if EVOwner by NIC
        //         if (!string.IsNullOrEmpty(nic) && role == "Owner")
        //         {
        //             var owner = await _evOwnerService.GetEVOwnerByNIC(nic);
        //             if (owner != null)
        //             {
        //                 return Ok(new EVOwnerDto
        //                 {
        //                     NIC = owner.NIC,
        //                     FullName = owner.FullName,
        //                     Email = owner.Email,
        //                     IsActive = owner.IsActive,
        //                     CreatedAt = owner.CreatedAt
        //                 });
        //             }
        //         }

        //         // Otherwise, fallback to normal User
        //         var user = await _userService.GetUserById(userId);
        //         if (user != null)
        //         {
        //             return Ok(new UserDto
        //             {
        //                 Id = user.Id,
        //                 FullName = user.FullName,
        //                 Email = user.Email,
        //                 Role = user.Role,
        //                 IsActive = user.IsActive,
        //                 CreatedAt = user.CreatedAt,

        //                 StationName = user.StationName,
        //                 StationLocation = user.StationLocation
        //             });
        //         }

        //         return NotFound(new { message = "User not found" });
        //     }
        //     catch (Exception ex)
        //     {
        //         _logger.LogError(ex, "An error occurred while fetching user details.");
        //         return StatusCode(500, new { message = "An unexpected error occurred." });
        //     }
        // }

        [HttpGet("me")]
        [Authorize]
        public async Task<IActionResult> Me()
        {
            try
            {
                var role = User.Claims.FirstOrDefault(c => c.Type == ClaimTypes.Role)?.Value;
                var userId = User.Claims.FirstOrDefault(c => c.Type == ClaimTypes.NameIdentifier)?.Value;
                var nic = "";

                if (role == "Owner")
                {
                    nic = userId;
                    userId = "";
                }

                if (role == null)
                    return Unauthorized(new { message = "Invalid token" });

                if (string.IsNullOrEmpty(userId) && string.IsNullOrEmpty(nic))
                    return Unauthorized(new { message = "Invalid token" });

                // ✅ Owner flow — now includes phone & reactivationRequested
                if (!string.IsNullOrEmpty(nic) && role == "Owner")
                {
                    var owner = await _evOwnerService.GetEVOwnerByNIC(nic);
                    if (owner != null)
                    {
                        return Ok(new
                        {
                            nic = owner.NIC,
                            fullName = owner.FullName,
                            email = owner.Email,
                            phone = owner.Phone,                       // ✅ Added field
                            isActive = owner.IsActive,
                            reactivationRequested = owner.ReactivationRequested,  // ✅ Added field
                            createdAt = owner.CreatedAt
                        });
                    }
                }

                // ✅ Operator/Admin fallback (as before)
                var user = await _userService.GetUserById(userId);
                if (user != null)
                {
                    return Ok(new
                    {
                        id = user.Id,
                        fullName = user.FullName,
                        email = user.Email,
                        role = user.Role,
                        isActive = user.IsActive,
                        createdAt = user.CreatedAt,
                        stationName = user.StationName,
                        stationLocation = user.StationLocation
                    });
                }

                return NotFound(new { message = "User not found" });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "An error occurred while fetching user details.");
                return StatusCode(500, new { message = "An unexpected error occurred." });
            }
        }
        //logout
        [HttpPost("logout")]
        [Authorize]
        public IActionResult Logout()
        {
            return Ok(new { message = "Logged out successfully" });
        }

        //reset password
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
                _logger.LogError(ex, "An unexpected error occurred during password reset.");
                return StatusCode(500, new { message = "An unexpected error occurred." });
            }
        }

        //forgot password
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
                _logger.LogError(ex, "An unexpected error occurred during forgot password process.");
                return StatusCode(500, new { message = "An unexpected error occurred." });
            }
        }

        //change password
        [HttpPost("change-password")]
        [Authorize]
        public async Task<IActionResult> ChangePassword([FromBody] ChangePasswordDto changePasswordDto)
        {
            try
            {
                var userId = User.Claims.FirstOrDefault(c => c.Type == ClaimTypes.NameIdentifier)?.Value;

                if (string.IsNullOrEmpty(userId))
                    return Unauthorized(new { message = "Invalid token" });

                await _authService.ChangePassword(userId, changePasswordDto);

                _logger.LogInformation("ChangePassword: Password changed successfully for user {UserId}", userId);
                return Ok(new { message = "Password changed successfully" });
            }
            catch (KeyNotFoundException ex)
            {
                _logger.LogWarning("ChangePassword: User not found");
                return NotFound(new { message = ex.Message });
            }
            catch (AuthenticationException ex)
            {
                _logger.LogWarning("ChangePassword: Authentication failed - {Message}", ex.Message);
                return BadRequest(new { message = ex.Message });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "ChangePassword: Unexpected error occurred");
                return StatusCode(500, new { message = "An unexpected error occurred" });
            }
        }
    }
}
