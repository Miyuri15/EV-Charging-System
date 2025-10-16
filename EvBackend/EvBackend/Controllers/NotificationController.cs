// --------------------------------------------------------------
// File Name: NotificationController.cs
// Author: Hasindu Koshitha
// Description: Controller for managing user notifications.
// Created On: 18/09/2025
// --------------------------------------------------------------

using Microsoft.AspNetCore.Mvc;
using EvBackend.Services.Interfaces;
using EvBackend.Entities;
using Microsoft.AspNetCore.Authorization;
using EvBackend.DTOs;

namespace EvBackend.Controllers
{
    [ApiController]
    [Route("api/notifications")]
    public class NotificationController : ControllerBase
    {
        private readonly INotificationService _notificationService;
        private readonly ILogger<NotificationController> _logger;

        public NotificationController(INotificationService notificationService, ILogger<NotificationController> logger)
        {
            _notificationService = notificationService;
            _logger = logger;
        }

        // POST /api/notifications
        [HttpPost]
        [Authorize]
        public async Task<IActionResult> SendNotification([FromBody] NotificationDto notification)
        {
            try
            {
                await _notificationService.SendNotification(notification.UserId, notification.Message);
                _logger.LogInformation("Notification sent successfully to UserId: {UserId}", notification.UserId);

                return Ok(notification);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error occurred while sending notification to UserId: {UserId}", notification.UserId);
                return StatusCode(500, new { message = "Failed to send notification.", details = ex.Message });
            }
        }

        // GET /api/notifications/user/{userId}
        [HttpGet("user/{userId}")]
        [Authorize]
        public async Task<IActionResult> GetUserNotifications(string userId)
        {
            try
            {
                if (string.IsNullOrEmpty(userId))
                {
                    _logger.LogWarning("Invalid request - User ID is null or empty.");
                    return BadRequest(new { message = "User ID cannot be null or empty." });
                }
                var notifications = await _notificationService.GetUserNotifications(userId);

                if (notifications == null || !notifications.Any())
                {
                    return Ok(new List<object>());
                }

                return Ok(notifications);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error occurred while fetching notifications for UserId: {UserId}", userId);
                return StatusCode(500, new { message = "An error occurred while fetching notifications.", details = ex.Message });
            }
        }

        // GET /api/notifications/user
        [HttpGet("user")]
        [Authorize]
        public async Task<IActionResult> GetUserNotifications()
        {
            try
            {
                var userId = User.Claims.FirstOrDefault(c => c.Type == System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;

                if (string.IsNullOrEmpty(userId))
                {
                    _logger.LogWarning("Unauthorized request - User ID not found in token claims.");
                    return Unauthorized(new { message = "User ID not found." });
                }

                var notifications = await _notificationService.GetUserNotifications(userId);

                if (notifications == null || !notifications.Any())
                {
                    return Ok(new List<object>());
                }

                return Ok(notifications);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error fetching notifications for authenticated user.");
                return StatusCode(500, new { message = "An error occurred while fetching notifications.", details = ex.Message });
            }
        }

        // PATCH /api/notifications/{notificationId}/read
        [HttpPatch("{notificationId}/read")]
        [Authorize]
        public async Task<IActionResult> MarkNotificationAsRead(string notificationId)
        {
            try
            {
                await _notificationService.MarkNotificationAsRead(notificationId);

                return Ok(new { message = "Notification marked as read successfully." });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error marking notification as read. NotificationId: {NotificationId}", notificationId);
                return StatusCode(500, new { message = "Failed to mark notification as read.", details = ex.Message });
            }
        }

        // DELETE /api/notifications/{notificationId}
        [HttpDelete("{notificationId}")]
        [Authorize]
        public async Task<IActionResult> DeleteNotification(string notificationId)
        {
            try
            {
                await _notificationService.DeleteNotification(notificationId);

                return Ok(new { message = "Notification deleted successfully." });
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error deleting notification. NotificationId: {NotificationId}", notificationId);
                return StatusCode(500, new { message = "Failed to delete notification.", details = ex.Message });
            }
        }
    }
}
