// --------------------------------------------------------------
// File Name: StationService.cs
// Author: Denuwan Sathsara 
// Description: Implements business logic for station management
// Updated: 09/10/2025 – Removed timeslot generation from station creation.
// --------------------------------------------------------------

using System;
using EvBackend.Entities;
using EvBackend.Models.DTOs;
using EvBackend.Services.Interfaces;
using MongoDB.Driver;
using System.Linq;

namespace EvBackend.Services
{
    public class StationService : IStationService
    {
        private readonly IMongoCollection<Station> _stations;
        private readonly IMongoCollection<Slot> _slots;
        private readonly IMongoCollection<TimeSlot> _timeSlots;
        private readonly IMongoCollection<Booking> _bookings;
        private readonly GeocodingService _geocoding;

        public StationService(IMongoDatabase database, GeocodingService geocoding)
        {
            _stations = database.GetCollection<Station>("Stations");
            _slots = database.GetCollection<Slot>("Slots");
            _timeSlots = database.GetCollection<TimeSlot>("TimeSlots");
            _bookings = database.GetCollection<Booking>("Bookings");
            _geocoding = geocoding;
        }

        // ✅ Creates station + slots only (no time slots)
        public async Task<StationDto> CreateStationAsync(CreateStationDto dto)
        {
            var coords = await _geocoding.GetCoordinatesAsync(dto.Location);

            // 1️⃣ Create the Station
            var station = new Station
            {
                Name = dto.Name,
                Location = dto.Location,
                Type = dto.Type,
                Capacity = dto.Capacity,
                AvailableSlots = dto.Capacity,
                IsActive = true,
                Latitude = coords?.lat ?? 0,
                Longitude = coords?.lng ?? 0,
                CreatedAt = DateTime.UtcNow,
                UpdatedAt = DateTime.UtcNow
            };

            await _stations.InsertOneAsync(station);

            // 2️⃣ Create Slots
            var allSlots = new List<Slot>();
            for (int i = 1; i <= dto.Capacity; i++)
            {
                var slot = new Slot
                {
                    SlotId = MongoDB.Bson.ObjectId.GenerateNewId().ToString(),
                    StationId = station.StationId,
                    Number = i,
                    Status = "Available",
                    CreatedAt = DateTime.UtcNow,
                    UpdatedAt = DateTime.UtcNow
                };
                allSlots.Add(slot);
            }

            await _slots.InsertManyAsync(allSlots);

            // 3️⃣ Generate Time Slots for 7 Days
            var sriLankaTz = TimeZoneInfo.FindSystemTimeZoneById("Sri Lanka Standard Time");
            double sessionMinutes = 120;
            string[] fixedSlots = { "01:15", "03:30", "05:45", "08:00", "10:15",
                                    "12:30", "14:45", "17:00", "19:15", "21:30" };

            var allTimeSlots = new List<TimeSlot>();
            var slotTimeMap = new Dictionary<string, List<string>>();

            for (int dayOffset = 0; dayOffset < 7; dayOffset++)
            {
                var targetDay = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, sriLankaTz).Date.AddDays(dayOffset);

                foreach (var slot in allSlots)
                {
                    var timeSlotIds = new List<string>();

                    foreach (var startStr in fixedSlots)
                    {
                        var startLocal = DateTime.Parse($"{targetDay:yyyy-MM-dd} {startStr}");
                        var endLocal = startLocal.AddMinutes(sessionMinutes);
                        var startUtc = TimeZoneInfo.ConvertTimeToUtc(startLocal, sriLankaTz);
                        var endUtc = TimeZoneInfo.ConvertTimeToUtc(endLocal, sriLankaTz);

                        var tsId = MongoDB.Bson.ObjectId.GenerateNewId().ToString();
                        timeSlotIds.Add(tsId);

                        allTimeSlots.Add(new TimeSlot
                        {
                            TimeSlotId = tsId,
                            StationId = station.StationId,
                            SlotId = slot.SlotId,
                            StartTime = startUtc,
                            EndTime = endUtc,
                            Status = "Available"
                        });
                    }

                    slotTimeMap[slot.SlotId] = timeSlotIds;
                }
            }

            if (allTimeSlots.Any())
                await _timeSlots.InsertManyAsync(allTimeSlots);

            // 4️⃣ Update each Slot with its time slot references
            foreach (var slot in allSlots)
            {
                var update = Builders<Slot>.Update
                    .Set(s => s.TimeSlotIds, slotTimeMap[slot.SlotId])
                    .Set(s => s.UpdatedAt, DateTime.UtcNow);

                await _slots.UpdateOneAsync(s => s.SlotId == slot.SlotId, update);
            }

            // 5️⃣ Update Station with SlotIds
            station.SlotIds = allSlots.Select(s => s.SlotId).ToList();
            await _stations.UpdateOneAsync(
                Builders<Station>.Filter.Eq(s => s.StationId, station.StationId),
                Builders<Station>.Update.Set(s => s.SlotIds, station.SlotIds)
            );

            return ToDto(station);
        }


        // ✅ All other functions remain the same as before
        // (Update, Deactivate, Get, Search, Delete, etc.)
        public async Task<StationDto> UpdateStationAsync(string stationId, UpdateStationDto dto)
        {
            // Step 1: Retrieve the station from the database
            var station = await _stations.Find(s => s.StationId == stationId).FirstOrDefaultAsync();
            if (station == null) return null;  // If station doesn't exist, return null

            // Step 2: Get new coordinates for location change
            var coords = await _geocoding.GetCoordinatesAsync(dto.Location);

            // Step 3: Prepare the update operation for station details
            var filter = Builders<Station>.Filter.Eq(s => s.StationId, stationId);
            var update = Builders<Station>.Update
                .Set(s => s.Name, dto.Name)
                .Set(s => s.Location, dto.Location)
                .Set(s => s.Latitude, coords?.lat ?? station.Latitude)  // Update latitude if location is changed
                .Set(s => s.Longitude, coords?.lng ?? station.Longitude)  // Update longitude if location is changed
                .Set(s => s.Type, dto.Type)
                .Set(s => s.AvailableSlots, dto.AvailableSlots)
                .Set(s => s.UpdatedAt, DateTime.UtcNow);

            // Step 4: Apply the update to the station
            await _stations.UpdateOneAsync(filter, update);

            // Step 5: Handle Slot updates
            if (dto.SlotUpdates != null && dto.SlotUpdates.Any())
            {
                foreach (var slotUpdate in dto.SlotUpdates)
                {
                    // Filter for bookings related to this slot
                    var bookingFilter = Builders<Booking>.Filter.And(
                        Builders<Booking>.Filter.Eq(b => b.SlotId, slotUpdate.SlotId),
                        Builders<Booking>.Filter.In(b => b.Status, new[] { "Pending", "Approved" })
                    );

                    if (slotUpdate.Action == SlotAction.Remove)
                    {
                        // Remove the slot
                        var deleteFilter = Builders<Slot>.Filter.Eq(s => s.SlotId, slotUpdate.SlotId);
                        await _slots.DeleteOneAsync(deleteFilter);
                        station.SlotIds.Remove(slotUpdate.SlotId);

                        // Cancel related bookings
                        var cancelUpdate = Builders<Booking>.Update
                            .Set(b => b.Status, "Cancelled")
                            .Set(b => b.UpdatedAt, DateTime.UtcNow);
                        await _bookings.UpdateManyAsync(bookingFilter, cancelUpdate);
                    }
                    else if (slotUpdate.Action == SlotAction.Update)
                    {
                        // Update slot status
                        var filterSlot = Builders<Slot>.Filter.Eq(s => s.SlotId, slotUpdate.SlotId);
                        var updateSlot = Builders<Slot>.Update.Set(s => s.Status, slotUpdate.Status);
                        await _slots.UpdateOneAsync(filterSlot, updateSlot);

                        // If new status is NOT 'Available', cancel related bookings
                        if (!string.Equals(slotUpdate.Status, "Available", StringComparison.OrdinalIgnoreCase))
                        {
                            var cancelUpdate = Builders<Booking>.Update
                                .Set(b => b.Status, "Cancelled")
                                .Set(b => b.UpdatedAt, DateTime.UtcNow);
                            await _bookings.UpdateManyAsync(bookingFilter, cancelUpdate);
                        }
                    }
                    else if (slotUpdate.Action == SlotAction.Add)
                    {
                        // Add new slot to the station
                        var lastSlot = await _slots.Find(s => s.StationId == stationId)
                                                   .SortByDescending(s => s.Number)
                                                   .FirstOrDefaultAsync();

                        int nextNumber = lastSlot != null ? lastSlot.Number + 1 : 1;

                        var newSlot = new Slot
                        {
                            StationId = stationId,
                            Number = nextNumber,
                            Status = slotUpdate.Status
                        };

                        await _slots.InsertOneAsync(newSlot);
                        station.SlotIds.Add(newSlot.SlotId);

                        // Update station’s SlotIds and Capacity
                        await _stations.UpdateOneAsync(
                            Builders<Station>.Filter.Eq(s => s.StationId, stationId),
                            Builders<Station>.Update
                                .Set(s => s.SlotIds, station.SlotIds)
                                .Set(s => s.Capacity, station.SlotIds.Count)
                        );
                    }
                }

                // Sync SlotIds back to the Station
                await _stations.UpdateOneAsync(
                    Builders<Station>.Filter.Eq(s => s.StationId, stationId),
                    Builders<Station>.Update.Set(s => s.SlotIds, station.SlotIds)
                );
            }

            // Step 6: Recalculate capacity and available slots
            var updatedSlots = await _slots.Find(s => s.StationId == stationId).ToListAsync();
            var capacity = updatedSlots.Count;
            var availableSlots = updatedSlots.Count(s => s.Status == "Available");

            await _stations.UpdateOneAsync(filter,
                Builders<Station>.Update
                    .Set(s => s.Capacity, capacity)
                    .Set(s => s.AvailableSlots, availableSlots)
            );

            // Step 7: Handle Time Slot updates for the slots
            var sriLankaTz = TimeZoneInfo.FindSystemTimeZoneById("Sri Lanka Standard Time");
            double sessionMinutes = 120;
            string[] fixedSlots = { "01:15", "03:30", "05:45", "08:00", "10:15",
                            "12:30", "14:45", "17:00", "19:15", "21:30" };

            var allTimeSlots = new List<TimeSlot>();
            var slotTimeMap = new Dictionary<string, List<string>>();

            for (int dayOffset = 0; dayOffset < 7; dayOffset++)
            {
                var targetDay = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, sriLankaTz).Date.AddDays(dayOffset);

                foreach (var slot in updatedSlots)
                {
                    var timeSlotIds = new List<string>();

                    foreach (var startStr in fixedSlots)
                    {
                        var startLocal = DateTime.Parse($"{targetDay:yyyy-MM-dd} {startStr}");
                        var endLocal = startLocal.AddMinutes(sessionMinutes);
                        var startUtc = TimeZoneInfo.ConvertTimeToUtc(startLocal, sriLankaTz);
                        var endUtc = TimeZoneInfo.ConvertTimeToUtc(endLocal, sriLankaTz);

                        var tsId = MongoDB.Bson.ObjectId.GenerateNewId().ToString();
                        timeSlotIds.Add(tsId);

                        allTimeSlots.Add(new TimeSlot
                        {
                            TimeSlotId = tsId,
                            StationId = station.StationId,
                            SlotId = slot.SlotId,
                            StartTime = startUtc,
                            EndTime = endUtc,
                            Status = "Available"
                        });
                    }

                    slotTimeMap[slot.SlotId] = timeSlotIds;
                }
            }

            if (allTimeSlots.Any())
                await _timeSlots.InsertManyAsync(allTimeSlots);

            // Step 8: Update each Slot with its time slot references
            foreach (var slot in updatedSlots)
            {
                var slotUpdateDef = Builders<Slot>.Update
                    .Set(s => s.TimeSlotIds, slotTimeMap[slot.SlotId])
                    .Set(s => s.UpdatedAt, DateTime.UtcNow);

                await _slots.UpdateOneAsync(s => s.SlotId == slot.SlotId, slotUpdateDef);
            }

            // Step 9: Return DTO with updated information
            var updatedStation = await _stations.Find(s => s.StationId == stationId).FirstOrDefaultAsync();
            return updatedStation != null ? ToDto(updatedStation) : null;
        }


        public async Task<bool> ToggleStationStatusAsync(string stationId)
        {
            // First, check if the station has active bookings
            if (await HasActiveBookingsAsync(stationId))
                throw new InvalidOperationException("Cannot modify station status with active bookings.");

            // Get the current station status
            var station = await _stations.Find(s => s.StationId == stationId).FirstOrDefaultAsync();
            if (station == null) return false;

            // Toggle the status (if active, set to inactive, and vice versa)
            var newStatus = !station.IsActive;

            var filter = Builders<Station>.Filter.Eq(s => s.StationId, stationId);
            var update = Builders<Station>.Update.Set(s => s.IsActive, newStatus);
            var result = await _stations.UpdateOneAsync(filter, update);

            return result.ModifiedCount > 0; // Returns true if update is successful
        }

        public async Task<StationDto> GetStationByIdAsync(string stationId)
        {
            var station = await _stations.Find(s => s.StationId == stationId).FirstOrDefaultAsync();
            return station != null ? ToDto(station) : null;
        }

        public async Task<PagedResultDto<StationDto>> GetAllStationsAsync(
            bool onlyActive = false, int page = 1, int pageSize = 10)
        {
            page = page < 1 ? 1 : page;
            var skip = (page - 1) * pageSize;

            var filter = onlyActive
                ? Builders<Station>.Filter.Eq(s => s.IsActive, true)
                : Builders<Station>.Filter.Empty;

            var pipeline = _stations.Aggregate()
                .Match(filter)
                .Lookup<Station, Slot, StationWithSlots>(
                    _slots,
                    s => s.StationId,
                    sl => sl.StationId,
                    sws => sws.Slots)
                .Skip(skip)
                .Limit(pageSize);

            var stations = await pipeline.ToListAsync();

            var totalCount = await _stations.CountDocumentsAsync(filter);

            return new PagedResultDto<StationDto>
            {
                TotalCount = (int)totalCount,
                TotalPages = (int)Math.Ceiling((double)totalCount / pageSize),
                CurrentPage = page,
                PageSize = pageSize,
                Items = stations.Select(s => new StationDto
                {
                    StationId = s.StationId,
                    Name = s.Name,
                    Location = s.Location,
                    Latitude = s.Latitude,
                    Longitude = s.Longitude,
                    Type = s.Type,
                    Capacity = s.Capacity,
                    AvailableSlots = s.AvailableSlots,
                    IsActive = s.IsActive,
                    Slots = s.Slots?.Select(slot => new SlotDto
                    {
                        SlotId = slot.SlotId,
                        StationId = slot.StationId,
                        Number = slot.Number,
                        Status = slot.Status
                    }).ToList()
                })
            };
        }


        public async Task<IEnumerable<StationDto>> SearchStationsAsync(string type, string location)
        {
            var filter = Builders<Station>.Filter.Empty;

            if (!string.IsNullOrEmpty(type))
                filter &= Builders<Station>.Filter.Eq(s => s.Type, type);

            if (!string.IsNullOrEmpty(location))
                filter &= Builders<Station>.Filter.Regex(
                    s => s.Location, new MongoDB.Bson.BsonRegularExpression(location, "i"));

            var stations = await _stations.Find(filter).ToListAsync();
            return stations.Select(ToDto);
        }

        public async Task<bool> HasActiveBookingsAsync(string stationId)
        {
            var bookings = _stations.Database.GetCollection<Booking>("Bookings");
            var filter = Builders<Booking>.Filter.Eq(b => b.StationId, stationId) &
                         Builders<Booking>.Filter.In(b => b.Status, new[] { "Pending", "Approved" });
            return await bookings.Find(filter).AnyAsync();
        }

        public async Task<IEnumerable<StationDto>> GetNearbyStationsAsync(double latitude, double longitude, double radiusKm)
        {
            var list = await _stations.Find(s => s.IsActive).ToListAsync();
            return list.Where(s => GetDistanceKm(latitude, longitude, s.Latitude, s.Longitude) <= radiusKm)
                       .Select(ToDto);
        }

        private double GetDistanceKm(double lat1, double lon1, double lat2, double lon2)
        {
            const double R = 6371;
            var dLat = (lat2 - lat1) * Math.PI / 180.0;
            var dLon = (lon2 - lon1) * Math.PI / 180.0;
            var a = Math.Sin(dLat / 2) * Math.Sin(dLat / 2) +
                    Math.Cos(lat1 * Math.PI / 180.0) * Math.Cos(lat2 * Math.PI / 180.0) *
                    Math.Sin(dLon / 2) * Math.Sin(dLon / 2);
            return R * 2 * Math.Asin(Math.Sqrt(a));
        }

        public async Task<List<StationDto>> GetNearbyStationsByTypeAsync(string type, double latitude, double longitude, double radiusKm)
        {
            try
            {
                if (string.IsNullOrWhiteSpace(type))
                    throw new ArgumentException("Type is required (AC/DC)");

                // Step 1: Get nearby stations
                var nearbyStations = await GetNearbyStationsAsync(latitude, longitude, radiusKm);

                // ✅ FIX: use Count() extension and null check properly
                if (nearbyStations == null || !nearbyStations.Any())
                    return new List<StationDto>();

                // Step 2: Filter by type (case-insensitive)
                var filtered = nearbyStations
                    .Where(s => s != null &&
                                !string.IsNullOrEmpty(s.Type) &&
                                string.Equals(s.Type, type, StringComparison.OrdinalIgnoreCase))
                    .ToList();

                return filtered;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[StationService] Error in GetNearbyStationsByTypeAsync: {ex.Message}");
                throw;
            }
        }


        private StationDto ToDto(Station station)
        {
            var slotEntities = _slots.Find(s => s.StationId == station.StationId).ToList();

            return new StationDto
            {
                StationId = station.StationId,
                Name = station.Name,
                Location = station.Location,
                Latitude = station.Latitude,
                Longitude = station.Longitude,
                Type = station.Type,
                Capacity = station.Capacity,
                AvailableSlots = station.AvailableSlots,
                IsActive = station.IsActive,
                Slots = slotEntities.Select(s => new SlotDto
                {
                    SlotId = s.SlotId,
                    StationId = s.StationId,
                    Number = s.Number,
                    Status = s.Status
                }).ToList()
            };
        }

        public async Task<bool> DeleteStationWithRelationsAsync(string stationId)
        {
            var station = await _stations.Find(s => s.StationId == stationId).FirstOrDefaultAsync();
            if (station == null)
                return false;

            await _timeSlots.DeleteManyAsync(t => t.StationId == stationId);
            await _slots.DeleteManyAsync(s => s.StationId == stationId);
            await _stations.DeleteOneAsync(s => s.StationId == stationId);
            return true;
        }

        public async Task<object> GetActiveInactiveStationCountAsync()
        {
            var activeCountFilter = Builders<Station>.Filter.Eq(s => s.IsActive, true);
            var inactiveCountFilter = Builders<Station>.Filter.Eq(s => s.IsActive, false);

            // Count active stations
            var activeCount = await _stations.CountDocumentsAsync(activeCountFilter);

            // Count inactive stations
            var inactiveCount = await _stations.CountDocumentsAsync(inactiveCountFilter);

            return new
            {
                ActiveStations = activeCount,
                InactiveStations = inactiveCount
            };
        }

        public async Task<IEnumerable<StationNameDto>> GetStationNameSuggestionsAsync(string? type = null, string? location = null)
        {
            var filter = Builders<Station>.Filter.Empty;

            if (!string.IsNullOrEmpty(type))
                filter &= Builders<Station>.Filter.Eq(s => s.Type, type);

            if (!string.IsNullOrEmpty(location))
                filter &= Builders<Station>.Filter.Regex(
                    s => s.Location, new MongoDB.Bson.BsonRegularExpression(location, "i")
                );

            var stations = await _stations.Find(filter)
                                        .Project(s => new StationNameDto
                                        {
                                            StationId = s.StationId,
                                            Name = s.Name,
                                            Location = s.Location,
                                            Latitude = s.Latitude,
                                            Longitude = s.Longitude
                                        })
                                        .ToListAsync();

            return stations;
        }

        public class StationWithSlots : Station
        {
            public List<Slot> Slots { get; set; }
        }


    }
}

