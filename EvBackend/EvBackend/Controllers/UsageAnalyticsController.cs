// --------------------------------------------------------------
// File Name: UsageAnalyticsController.cs
// Author: Denuwan Sathsara
// Description: Controller for retrieving usage analytics data.
// Created/Updated On: 27/09/2025
// --------------------------------------------------------------
using System;
using Microsoft.AspNetCore.Mvc;
using EvBackend.Services.Interfaces;
using Microsoft.AspNetCore.Authorization;
namespace EvBackend.Controllers;

[Route("api/analytics")]
[ApiController]
public class UsageAnalyticsController : ControllerBase
{
    private readonly IUsageAnalyticsService _usageAnalyticsService;

    public UsageAnalyticsController(IUsageAnalyticsService usageAnalyticsService)
    {
        _usageAnalyticsService = usageAnalyticsService;
    }

    // Get usage analytics (admin)
    [HttpGet("usage")]
    //[Authorize(Roles = "Admin")]
    public async Task<IActionResult> GetUsageAnalytics()
    {
        try
        {
            var result = await _usageAnalyticsService.GetUsageAnalyticsAsync();
            return Ok(result);
        }
        catch (Exception ex)
        {
            return StatusCode(500, new { message = "Unexpected error", details = ex.Message });
        }
    }
}