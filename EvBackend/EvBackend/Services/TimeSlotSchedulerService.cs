using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using EvBackend.Entities;
using MongoDB.Driver;
using Microsoft.Extensions.Logging;
using MongoDB.Bson;

namespace EvBackend.Services
{
    public class TimeSlotSchedulerService
    {
        private readonly IMongoCollection<Station> _stations;
        private readonly IMongoCollection<Slot> _slots;
        private readonly IMongoCollection<TimeSlot> _timeSlots;
        private readonly ILogger<TimeSlotSchedulerService> _logger;

        public TimeSlotSchedulerService(IMongoDatabase database, ILogger<TimeSlotSchedulerService> logger)
        {
            _stations = database.GetCollection<Station>("Stations");
            _slots = database.GetCollection<Slot>("Slots");
            _timeSlots = database.GetCollection<TimeSlot>("TimeSlots");
            _logger = logger;
        }

        public async Task CleanupAndGenerateNextDayAsync()
        {
            try
            {
                _logger.LogInformation("🕛 Running daily timeslot maintenance job...");

                var sriLankaTz = TimeZoneInfo.FindSystemTimeZoneById("Sri Lanka Standard Time");
                var todaySL = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, sriLankaTz).Date;

                // Keep 7-day rolling window
                var dayToDelete = todaySL.AddDays(-1);  // yesterday
                var dayToAdd = todaySL.AddDays(6);      // new 7th day

                // Convert both to UTC for DB queries
                var deleteStartUtc = TimeZoneInfo.ConvertTimeToUtc(dayToDelete, sriLankaTz);
                var deleteEndUtc = TimeZoneInfo.ConvertTimeToUtc(dayToDelete.AddDays(1), sriLankaTz);

                // Delete expired day
                var deleteFilter = Builders<TimeSlot>.Filter.Gte(t => t.StartTime, deleteStartUtc) &
                                   Builders<TimeSlot>.Filter.Lt(t => t.StartTime, deleteEndUtc);
                var deleted = await _timeSlots.DeleteManyAsync(deleteFilter);
                _logger.LogInformation("🧹 Deleted {Count} time slots for {Date}.", deleted.DeletedCount, dayToDelete);

                // --- REMOVE DUPLICATES (same Station, Slot, Start & End Time) ---
                var duplicateGroups = await _timeSlots.Aggregate()
                    .Group(new BsonDocument
                    {
                { "_id", new BsonDocument {
                    { "stationId", "$StationId" },
                    { "slotId", "$SlotId" },
                    { "startTime", "$StartTime" },
                    { "endTime", "$EndTime" }
                }},
                { "ids", new BsonDocument("$push", "$_id") },
                { "count", new BsonDocument("$sum", 1) }
                    })
                    .Match(new BsonDocument("count", new BsonDocument("$gt", 1)))
                    .ToListAsync();

                int duplicateCount = 0;

                foreach (var group in duplicateGroups)
                {
                    var ids = group["ids"].AsBsonArray.Select(x => x.AsObjectId).ToList();
                    var idsToDelete = ids.Skip(1).ToList();

                    if (idsToDelete.Any())
                    {
                        var dupFilter = Builders<TimeSlot>.Filter.In("_id", idsToDelete);
                        var result = await _timeSlots.DeleteManyAsync(dupFilter);
                        duplicateCount += (int)result.DeletedCount;
                    }
                }

                if (duplicateCount > 0)
                    _logger.LogInformation("🗑️ Removed {Count} duplicate timeslots with same Start/End times.", duplicateCount);

                // Check if the day-to-add already exists (duplicate protection)
                var addStartUtc = TimeZoneInfo.ConvertTimeToUtc(dayToAdd, sriLankaTz);
                var addEndUtc = TimeZoneInfo.ConvertTimeToUtc(dayToAdd.AddDays(1), sriLankaTz);
                var existingForDay = await _timeSlots.Find(t =>
                    t.StartTime >= addStartUtc && t.StartTime < addEndUtc
                ).AnyAsync();

                if (existingForDay)
                {
                    _logger.LogInformation("⏩ Skipping generation for {Date} — timeslots already exist.", dayToAdd);
                    _logger.LogInformation("🎯 Daily timeslot maintenance completed (no duplicates).");
                    return;
                }

                // Generate new day’s slots (for dayToAdd)
                string[] fixedSlots = { "01:15", "03:30", "05:45", "08:00", "10:15",
                                        "12:30", "14:45", "17:00", "19:15", "21:30" };
                double sessionMinutes = 120;

                var slots = await _slots.Find(_ => true).ToListAsync();
                var newTimeSlots = new List<TimeSlot>();
                var slotUpdates = new Dictionary<string, List<string>>();

                foreach (var slot in slots)
                {
                    var newIds = new List<string>();

                    foreach (var startStr in fixedSlots)
                    {
                        var startLocal = DateTime.Parse($"{dayToAdd:yyyy-MM-dd} {startStr}");
                        var endLocal = startLocal.AddMinutes(sessionMinutes);
                        var startUtc = TimeZoneInfo.ConvertTimeToUtc(startLocal, sriLankaTz);
                        var endUtc = TimeZoneInfo.ConvertTimeToUtc(endLocal, sriLankaTz);

                        var tsId = MongoDB.Bson.ObjectId.GenerateNewId().ToString();
                        newIds.Add(tsId);

                        newTimeSlots.Add(new TimeSlot
                        {
                            TimeSlotId = tsId,
                            StationId = slot.StationId,
                            SlotId = slot.SlotId,
                            StartTime = startUtc,
                            EndTime = endUtc,
                            Status = "Available"
                        });
                    }

                    slotUpdates[slot.SlotId] = newIds;
                }

                if (newTimeSlots.Any())
                    await _timeSlots.InsertManyAsync(newTimeSlots);

                // Update slots to include new day’s timeslot IDs
                foreach (var slot in slots)
                {
                    var update = Builders<Slot>.Update
                        .AddToSetEach(s => s.TimeSlotIds, slotUpdates[slot.SlotId])
                        .Set(s => s.UpdatedAt, DateTime.UtcNow);

                    await _slots.UpdateOneAsync(s => s.SlotId == slot.SlotId, update);
                }

                _logger.LogInformation("Generated {Count} new slots for {Date}.",
                    newTimeSlots.Count, dayToAdd.ToShortDateString());

                _logger.LogInformation("Daily timeslot maintenance completed successfully.");
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error during daily timeslot maintenance job.");
            }
        }

        public async Task<IEnumerable<TimeSlot>> GetTimeSlotsByDateAsync(string stationId, string slotId, DateTime date)
        {
            var startOfDay = date.Date.ToUniversalTime();
            var endOfDay = startOfDay.AddDays(1);

            var filter = Builders<TimeSlot>.Filter.Eq(t => t.StationId, stationId)
                        & Builders<TimeSlot>.Filter.Eq(t => t.SlotId, slotId)
                        & Builders<TimeSlot>.Filter.Gte(t => t.StartTime, startOfDay)
                        & Builders<TimeSlot>.Filter.Lt(t => t.StartTime, endOfDay);

            var result = await _timeSlots.Find(filter)
                                        .SortBy(t => t.StartTime)
                                        .ToListAsync();

            return result;
        }

        public async Task UpdateExpiredTimeSlotsToAvailableAsync()
        {
            try
            {
                var nowUtc = DateTime.UtcNow;

                // Find timeslots where EndTime is before now and status is not already "Available"
                var filter = Builders<TimeSlot>.Filter.Lt(t => t.EndTime, nowUtc) &
                             Builders<TimeSlot>.Filter.Ne(t => t.Status, "Available");

                var update = Builders<TimeSlot>.Update.Set(t => t.Status, "Available");

                var result = await _timeSlots.UpdateManyAsync(filter, update);

                _logger.LogInformation("Updated {Count} expired timeslots to 'Available' at {Time}.",
                    result.ModifiedCount, nowUtc);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error updating expired timeslots to 'Available'.");
            }
        }

       
        public async Task<IEnumerable<TimeSlot>> GetAvailableTimeSlotsAsync(string stationId, string slotId, DateTime date)
        {
            try
            {
                var startOfDay = date.Date.ToUniversalTime();
                var endOfDay = startOfDay.AddDays(1);

                var filter = Builders<TimeSlot>.Filter.Eq(t => t.StationId, stationId)
                            & Builders<TimeSlot>.Filter.Eq(t => t.SlotId, slotId)
                            & Builders<TimeSlot>.Filter.Gte(t => t.StartTime, startOfDay)
                            & Builders<TimeSlot>.Filter.Lt(t => t.StartTime, endOfDay)
                            & Builders<TimeSlot>.Filter.Eq(t => t.Status, "Available");

                var result = await _timeSlots.Find(filter)
                                            .SortBy(t => t.StartTime)
                                            .ToListAsync();

                return result;
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error getting available time slots for station {StationId}, slot {SlotId}, date {Date}", 
                    stationId, slotId, date);
                throw;
            }
        }
    }
}
