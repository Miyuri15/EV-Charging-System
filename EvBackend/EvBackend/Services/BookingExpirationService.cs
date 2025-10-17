// --------------------------------------------------------------
// File Name: BookingExpirationService.cs
// Author: Miyuri Lokuhewage
// Description: Background service to automatically expire bookings
// that have passed their end time.
// Created/Updated On: 09/10/2025
// --------------------------------------------------------------

using System;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using MongoDB.Driver;
using EvBackend.Entities;

namespace EvBackend.BackgroundJobs
{
    public class BookingExpirationService : BackgroundService
    {
        private readonly ILogger<BookingExpirationService> _logger;
        private readonly IMongoDatabase _db;
        private readonly IServiceProvider _serviceProvider;

        public BookingExpirationService(
            ILogger<BookingExpirationService> logger,
            IMongoDatabase db,
            IServiceProvider serviceProvider)
        {
            _logger = logger;
            _db = db;
            _serviceProvider = serviceProvider;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            _logger.LogInformation("✅ Booking Expiration Service started. Will check for expired bookings every 2 hours.");

            while (!stoppingToken.IsCancellationRequested)
            {
                try
                {
                    var nowUtc = DateTime.UtcNow;
                    var nowSL = ConvertUtcToSriLankaTime(nowUtc);
                    
                    _logger.LogInformation("🕐 Booking Expiration Service: Starting periodic check at {SriLankaTime} (UTC: {UtcTime})", 
                        nowSL.ToString("yyyy-MM-dd HH:mm:ss"), nowUtc.ToString("yyyy-MM-dd HH:mm:ss"));
                    
                    var expiredCount = await ProcessExpiredBookingsAsync();
                    
                    var completedUtc = DateTime.UtcNow;
                    var completedSL = ConvertUtcToSriLankaTime(completedUtc);
                    var nextCheckSL = completedSL.AddHours(2);
                    
                    if (expiredCount > 0)
                    {
                        _logger.LogInformation("✅ Booking Expiration Service: Successfully expired {Count} booking(s) at {SriLankaTime}", 
                            expiredCount, completedSL.ToString("yyyy-MM-dd HH:mm:ss"));
                    }
                    else
                    {
                        _logger.LogInformation("ℹ️ Booking Expiration Service: No expired bookings found at {SriLankaTime}", 
                            completedSL.ToString("yyyy-MM-dd HH:mm:ss"));
                    }
                    
                    _logger.LogInformation("⏰ Booking Expiration Service: Next check at {NextCheckTime} SL Time", 
                        nextCheckSL.ToString("yyyy-MM-dd HH:mm:ss"));
                    
                    await Task.Delay(TimeSpan.FromHours(2), stoppingToken);
                }
                catch (Exception ex) when (!(ex is TaskCanceledException))
                {
                    var errorTimeSL = ConvertUtcToSriLankaTime(DateTime.UtcNow);
                    _logger.LogError(ex, "❌ Booking Expiration Service: Error occurred at {SriLankaTime}", 
                        errorTimeSL.ToString("yyyy-MM-dd HH:mm:ss"));
                    await Task.Delay(TimeSpan.FromMinutes(5), stoppingToken);
                }
            }

            _logger.LogInformation("🛑 Booking Expiration Service stopped.");
        }

        private async Task<int> ProcessExpiredBookingsAsync()
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");
            var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");
            
            var nowUtc = DateTime.UtcNow;
            var nowSL = ConvertUtcToSriLankaTime(nowUtc);
            
            _logger.LogDebug("🔍 Booking Expiration Service: Searching for expired bookings at {SriLankaTime}", 
                nowSL.ToString("yyyy-MM-dd HH:mm:ss"));
            
            // Find bookings that should be expired:
            // - Status is "Pending" or "Approved" 
            // - EndTime has passed
            // - Not already marked as expired
            var filter = Builders<Booking>.Filter.And(
                Builders<Booking>.Filter.In(b => b.Status, new[] { "Pending", "Approved" }),
                Builders<Booking>.Filter.Lt(b => b.EndTime, nowUtc),
                Builders<Booking>.Filter.Eq(b => b.IsExpired, false)
            );

            var expiredBookings = await bookingCol.Find(filter).ToListAsync();
            
            if (!expiredBookings.Any())
            {
                _logger.LogDebug("📭 Booking Expiration Service: No expired bookings found at {SriLankaTime}", 
                    nowSL.ToString("yyyy-MM-dd HH:mm:ss"));
                return 0;
            }

            _logger.LogInformation("📋 Booking Expiration Service: Found {Count} booking(s) to expire at {SriLankaTime}", 
                expiredBookings.Count, nowSL.ToString("yyyy-MM-dd HH:mm:ss"));

            int successfullyExpired = 0;

            foreach (var booking in expiredBookings)
            {
                var bookingEndTimeSL = ConvertUtcToSriLankaTime(booking.EndTime);
                _logger.LogInformation("🔄 Processing booking {BookingId} (Ended at {EndTime} SL Time)", 
                    booking.BookingId, bookingEndTimeSL.ToString("yyyy-MM-dd HH:mm:ss"));

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

                    var bookingResult = await bookingCol.UpdateOneAsync(
                        session,
                        Builders<Booking>.Filter.Eq(b => b.BookingId, booking.BookingId),
                        bookingUpdate
                    );

                    // Free up the timeslot for future bookings
                    var timeSlotResult = await timeSlotCol.UpdateOneAsync(
                        session,
                        Builders<TimeSlot>.Filter.Eq(t => t.TimeSlotId, booking.TimeSlotId),
                        Builders<TimeSlot>.Update.Set(t => t.Status, "Available")
                    );

                    await session.CommitTransactionAsync();
                    
                    successfullyExpired++;
                    _logger.LogInformation("✅ Successfully expired booking {BookingId} and freed timeslot {TimeSlotId} at {SriLankaTime}", 
                        booking.BookingId, booking.TimeSlotId, nowSL.ToString("yyyy-MM-dd HH:mm:ss"));
                }
                catch (Exception ex)
                {
                    await session.AbortTransactionAsync();
                    _logger.LogError(ex, "❌ Failed to expire booking {BookingId} at {SriLankaTime}", 
                        booking.BookingId, nowSL.ToString("yyyy-MM-dd HH:mm:ss"));
                }
            }

            return successfullyExpired;
        }

        // Helper method to convert UTC to Sri Lanka time
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
                    // Fallback: Sri Lanka is UTC+5:30
                    return utcTime.AddHours(5.5);
                }
            }
        }
    }
}