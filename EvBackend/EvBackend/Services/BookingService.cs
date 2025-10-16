// --------------------------------------------------------------
// File Name: BookingService.cs
// Author: Miyuri Lokuhewage
// Description: Implements booking logic for new station→timeslot→slot flow.
// Enforces 12h update/cancel rule, UTC storage, SL formatting.
// Created/Updated On: 09/10/2025
// --------------------------------------------------------------

using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading.Tasks;
using EvBackend.Entities;
using EvBackend.Models.DTOs;
using EvBackend.Services.Interfaces;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Driver;
using QRCoder;
using MongoDB.Bson;

namespace EvBackend.Services
{
    public class BookingService : IBookingService
    {
        private readonly IMongoDatabase _db;
        private readonly IEVOwnerService _evOwnerService;
        private readonly IStationService _stationService;


        public BookingService(IMongoDatabase db, IEVOwnerService evOwnerService, IStationService stationService)
        {
            _db = db;
            _evOwnerService = evOwnerService;
            _stationService = stationService;
        }

        // ------------------------------
        // Sri Lanka time formatting
        // ------------------------------
        private static TimeZoneInfo GetSriLankaTz()
        {
            try { return TimeZoneInfo.FindSystemTimeZoneById("Sri Lanka Standard Time"); }
            catch { return TimeZoneInfo.FindSystemTimeZoneById("Asia/Colombo"); }
        }

        private static string FormatSriLankaDate(DateTime utc)
        {
            var tz = GetSriLankaTz();
            var lt = TimeZoneInfo.ConvertTimeFromUtc(utc, tz);
            return lt.ToString("yyyy MMM dd");
        }

        private static string FormatSriLankaTime(DateTime utc)
        {
            var tz = GetSriLankaTz();
            var lt = TimeZoneInfo.ConvertTimeFromUtc(utc, tz);
            return lt.ToString("yyyy MMM dd, hh:mm tt"); // ✅ 12-hour format with AM/PM
        }

        // ------------------------------
        // Availability for UI
        // ------------------------------
        public async Task<IEnumerable<object>> GetAvailableTimeSlotsForStationAsync(string stationId, string dateYyyyMmDd)
        {
            if (string.IsNullOrWhiteSpace(dateYyyyMmDd))
                throw new ArgumentException("date (YYYY-MM-DD) is required");

            var stationCol = _db.GetCollection<Station>("Stations");
            var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");

            var station = await stationCol.Find(s => s.StationId == stationId).FirstOrDefaultAsync();
            if (station == null || !station.IsActive)
                throw new ArgumentException("Station not found or inactive");

            var tz = GetSriLankaTz();
            if (!DateTime.TryParse(dateYyyyMmDd, out var slDate))
                throw new ArgumentException("Invalid date");

            var dayStartSL = new DateTime(slDate.Year, slDate.Month, slDate.Day, 0, 0, 0);
            var dayEndSL = dayStartSL.AddDays(1).AddTicks(-1);
            var startUtc = TimeZoneInfo.ConvertTimeToUtc(dayStartSL, tz);
            var endUtc = TimeZoneInfo.ConvertTimeToUtc(dayEndSL, tz);

            var filter = Builders<TimeSlot>.Filter.And(
                Builders<TimeSlot>.Filter.Eq(t => t.StationId, stationId),
                Builders<TimeSlot>.Filter.Gte(t => t.StartTime, startUtc),
                Builders<TimeSlot>.Filter.Lte(t => t.StartTime, endUtc)
            );

            var slots = await timeSlotCol.Find(filter).ToListAsync();

            return slots
                .GroupBy(t => new { t.StartTime, t.EndTime })
                .Select(g => new
                {
                    StartTime = g.Key.StartTime,
                    EndTime = g.Key.EndTime,
                    TotalSlots = g.Count(),
                    AvailableCount = g.Count(t => t.Status == "Available"),
                    AnyAvailableTimeSlotId = g.FirstOrDefault(t => t.Status == "Available")?.TimeSlotId,
                    FormattedStart = FormatSriLankaTime(g.Key.StartTime),
                    FormattedEnd = FormatSriLankaTime(g.Key.EndTime),
                    FormattedDate = FormatSriLankaDate(g.Key.StartTime)
                })
                .OrderBy(x => x.StartTime)
                .ToList();
        }

        public async Task<IEnumerable<object>> GetAvailableSlotsForTimeSlotAsync(string timeSlotId)
        {
            var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");
            var slotCol = _db.GetCollection<Slot>("Slots");

            var ts = await timeSlotCol.Find(x => x.TimeSlotId == timeSlotId).FirstOrDefaultAsync();
            if (ts == null) throw new ArgumentException("TimeSlot not found");

            if (ts.Status != "Available")
                return Enumerable.Empty<object>();

            // same block = same Start/End & station; list slots whose corresponding block is Available
            var sameBlockFilter = Builders<TimeSlot>.Filter.And(
                Builders<TimeSlot>.Filter.Eq(x => x.StationId, ts.StationId),
                Builders<TimeSlot>.Filter.Eq(x => x.StartTime, ts.StartTime),
                Builders<TimeSlot>.Filter.Eq(x => x.EndTime, ts.EndTime),
                Builders<TimeSlot>.Filter.Eq(x => x.Status, "Available")
            );

            var availTs = await timeSlotCol.Find(sameBlockFilter).ToListAsync();
            var slotIds = availTs.Select(x => x.SlotId).Distinct().ToList();

            var slotDocs = await slotCol.Find(s => slotIds.Contains(s.SlotId) && s.Status == "Available").ToListAsync();

            return slotDocs.OrderBy(s => s.Number)
                           .Select(s => new { s.SlotId, s.Number });
        }

        // ------------------------------
        // Create Booking
        // ------------------------------
        public async Task<BookingDto> CreateBookingAsync(CreateBookingDto dto, string ownerId)
        {
            var stationCol = _db.GetCollection<Station>("Stations");
            var slotCol = _db.GetCollection<Slot>("Slots");
            var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");
            var bookingCol = _db.GetCollection<Booking>("Bookings");

            var station = await stationCol.Find(s => s.StationId == dto.StationId).FirstOrDefaultAsync();
            if (station == null) throw new ArgumentException("Station not found");
            if (!station.IsActive) throw new InvalidOperationException("Station inactive");

            var slot = await slotCol.Find(s => s.SlotId == dto.SlotId && s.StationId == dto.StationId).FirstOrDefaultAsync();
            if (slot == null) throw new ArgumentException("Slot not found");
            if (slot.Status != "Available") throw new InvalidOperationException("Slot is not available");

            var ts = await timeSlotCol.Find(t => t.TimeSlotId == dto.TimeSlotId).FirstOrDefaultAsync();
            if (ts == null) throw new ArgumentException("TimeSlot not found");
            if (ts.StationId != dto.StationId || ts.SlotId != dto.SlotId)
                throw new InvalidOperationException("TimeSlot does not belong to the selected station/slot");
            if (ts.Status != "Available") throw new InvalidOperationException("TimeSlot is not available");

            if (ts.StartTime < DateTime.UtcNow)
                throw new InvalidOperationException("Cannot book a past timeslot");

            var now = DateTime.UtcNow;
            var booking = new Booking
            {
                BookingId = MongoDB.Bson.ObjectId.GenerateNewId().ToString(),
                StationId = dto.StationId,
                SlotId = dto.SlotId,
                SlotNumber = slot.Number,
                TimeSlotId = dto.TimeSlotId,
                OwnerId = ownerId,
                Status = "Pending",
                StartTime = ts.StartTime,
                EndTime = ts.EndTime,
                CreatedAt = now,
                UpdatedAt = now
            };

            // QR with expiry at session end
            var token = Guid.NewGuid().ToString();
            booking.QrCode = token;
            booking.QrExpiresAt = ts.EndTime;

            using var qrGen = new QRCodeGenerator();
            var qrData = qrGen.CreateQrCode(token, QRCodeGenerator.ECCLevel.Q);
            var pngQr = new PngByteQRCode(qrData);
            booking.QrImageBase64 = Convert.ToBase64String(pngQr.GetGraphic(20));

            await bookingCol.InsertOneAsync(booking);

            // Only this (slot, start/end) timeslot becomes booked
            await timeSlotCol.UpdateOneAsync(
                t => t.TimeSlotId == ts.TimeSlotId,
                Builders<TimeSlot>.Update.Set(t => t.Status, "Booked")
            );

            return new BookingDto
            {
                BookingId = booking.BookingId,
                StationId = booking.StationId,
                SlotId = booking.SlotId,
                SlotNumber = booking.SlotNumber,
                TimeSlotId = booking.TimeSlotId,
                OwnerId = booking.OwnerId,
                Status = booking.Status,
                StartTime = booking.StartTime,
                EndTime = booking.EndTime,
                CreatedAt = booking.CreatedAt,
                UpdatedAt = booking.UpdatedAt,
                QrCode = booking.QrCode,
                QrExpiresAt = booking.QrExpiresAt,
                QrImageBase64 = booking.QrImageBase64,
                FormattedStartTime = FormatSriLankaTime(booking.StartTime),
                FormattedEndTime = FormatSriLankaTime(booking.EndTime),
                FormattedDate = FormatSriLankaDate(booking.StartTime)
            };
        }

        // ------------------------------
        // Update (Reschedule) ≥12h before start
        // ------------------------------
        public async Task<BookingDto> UpdateBookingAsync(string bookingId, UpdateBookingDto dto, string requesterId, string requesterRole)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");
            var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");
            var slotCol = _db.GetCollection<Slot>("Slots");

            var booking = await bookingCol.Find(b => b.BookingId == bookingId).FirstOrDefaultAsync();
            if (booking == null) return null;

            if (requesterRole == "Owner" && booking.OwnerId != requesterId)
                throw new UnauthorizedAccessException();

            if (DateTime.UtcNow > booking.StartTime.AddHours(-12))
                throw new InvalidOperationException("Cannot update booking within 12 hours of start");

            var newTs = await timeSlotCol.Find(t => t.TimeSlotId == dto.NewTimeSlotId).FirstOrDefaultAsync();
            if (newTs == null) throw new ArgumentException("New TimeSlot not found");
            if (newTs.Status != "Available") throw new InvalidOperationException("New TimeSlot is not available");

            var newSlot = await slotCol.Find(s => s.SlotId == dto.NewSlotId).FirstOrDefaultAsync();
            if (newSlot == null) throw new ArgumentException("New Slot not found");
            if (newSlot.Status != "Available") throw new InvalidOperationException("New Slot is not available");

            if (newTs.SlotId != newSlot.SlotId)
                throw new InvalidOperationException("New TimeSlot must belong to the selected new Slot");

            // Free old timeslot
            await timeSlotCol.UpdateOneAsync(
                t => t.TimeSlotId == booking.TimeSlotId,
                Builders<TimeSlot>.Update.Set(t => t.Status, "Available")
            );
            // Occupy new timeslot
            await timeSlotCol.UpdateOneAsync(
                t => t.TimeSlotId == newTs.TimeSlotId,
                Builders<TimeSlot>.Update.Set(t => t.Status, "Booked")
            );

            var update = Builders<Booking>.Update
                .Set(b => b.TimeSlotId, newTs.TimeSlotId)
                .Set(b => b.SlotId, newSlot.SlotId)
                .Set(b => b.SlotNumber, newSlot.Number)
                .Set(b => b.StartTime, newTs.StartTime)
                .Set(b => b.EndTime, newTs.EndTime)
                .Set(b => b.UpdatedAt, DateTime.UtcNow);

            var options = new FindOneAndUpdateOptions<Booking> { ReturnDocument = ReturnDocument.After };
            var updated = await bookingCol.FindOneAndUpdateAsync(b => b.BookingId == bookingId, update, options);

            return new BookingDto
            {
                BookingId = updated.BookingId,
                StationId = updated.StationId,
                SlotId = updated.SlotId,
                SlotNumber = updated.SlotNumber,
                TimeSlotId = updated.TimeSlotId,
                OwnerId = updated.OwnerId,
                Status = updated.Status,
                StartTime = updated.StartTime,
                EndTime = updated.EndTime,
                CreatedAt = updated.CreatedAt,
                UpdatedAt = updated.UpdatedAt,
                FormattedStartTime = FormatSriLankaTime(updated.StartTime),
                FormattedEndTime = FormatSriLankaTime(updated.EndTime),
                FormattedDate = FormatSriLankaDate(updated.StartTime)
            };
        }

        // ------------------------------
        // Cancel ≥12h before start
        // ------------------------------
        public async Task<bool> CancelBookingAsync(string bookingId, string requesterId, string requesterRole)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");
            var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");

            var booking = await bookingCol.Find(b => b.BookingId == bookingId).FirstOrDefaultAsync();
            if (booking == null) throw new ArgumentException("Booking not found");

            if (requesterRole == "Owner" && booking.OwnerId != requesterId)
                throw new UnauthorizedAccessException();

            if (DateTime.UtcNow > booking.StartTime.AddHours(-12))
                throw new InvalidOperationException("Cannot cancel booking within 12 hours of start");

            await bookingCol.UpdateOneAsync(
                b => b.BookingId == bookingId,
                Builders<Booking>.Update
                    .Set(b => b.Status, "Cancelled")
                    .Set(b => b.UpdatedAt, DateTime.UtcNow)
            );

            await timeSlotCol.UpdateOneAsync(
                t => t.TimeSlotId == booking.TimeSlotId,
                Builders<TimeSlot>.Update.Set(t => t.Status, "Available")
            );

            return true;
        }

        // ------------------------------
        // Gets
        // ------------------------------
        public async Task<BookingDto> GetBookingByIdAsync(string bookingId)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");
            var timeSlotCol = _db.GetCollection<TimeSlot>("TimeSlots");

            var b = await bookingCol.Find(x => x.BookingId == bookingId).FirstOrDefaultAsync();
            if (b == null) return null;

            var stationDto = await _stationService.GetStationByIdAsync(b.StationId);

            TimeSlot ts = null;
            if (!string.IsNullOrEmpty(b.TimeSlotId))
            {
                ts = await timeSlotCol.Find(t => t.TimeSlotId == b.TimeSlotId).FirstOrDefaultAsync();
            }

            var ownerDto = await _evOwnerService.GetEVOwnerById(b.OwnerId);

            return new BookingDto
            {
                BookingId = b.BookingId,
                StationId = b.StationId,
                SlotId = b.SlotId,
                SlotNumber = b.SlotNumber,
                TimeSlotId = b.TimeSlotId,
                OwnerId = b.OwnerId,
                Status = b.Status,
                StartTime = b.StartTime,
                EndTime = b.EndTime,
                CreatedAt = b.CreatedAt,
                UpdatedAt = b.UpdatedAt,
                QrCode = b.QrCode,
                QrExpiresAt = b.QrExpiresAt,
                QrImageBase64 = b.QrImageBase64,
                FormattedStartTime = FormatSriLankaTime(b.StartTime),
                FormattedEndTime = FormatSriLankaTime(b.EndTime),
                FormattedDate = FormatSriLankaDate(b.StartTime),

                StationName = stationDto?.Name,
                StationLocation = stationDto?.Location,

                SlotName = $"Slot {b.SlotNumber}",
                TimeSlotRange = ts != null
                    ? $"{FormatSriLankaTime(ts.StartTime).Split(", ")[1]} - {FormatSriLankaTime(ts.EndTime).Split(", ")[1]}"
                    : null,
                OwnerName = ownerDto?.FullName,
                CancellationReason = b.CancellationReason
            };
        }


        public async Task<IEnumerable<BookingDto>> GetBookingsByOwnerAsync(string ownerId)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");
            var stationCol = _db.GetCollection<Station>("Stations");

            var list = await bookingCol
                .Find(b => b.OwnerId == ownerId)
                .SortByDescending(b => b.StartTime)
                .ToListAsync();

            var bookingDtos = new List<BookingDto>();

            foreach (var b in list)
            {
                // Try to find matching station
                string stationName = "Unknown Station";
                try
                {
                    var station = await stationCol.Find(s => s.StationId == b.StationId).FirstOrDefaultAsync();
                    if (station != null && !string.IsNullOrEmpty(station.Name))
                        stationName = station.Name;
                }
                catch { /* ignore errors for invalid StationId */ }

                bookingDtos.Add(new BookingDto
                {
                    BookingId = b.BookingId,
                    StationId = b.StationId,
                    StationName = stationName,
                    SlotId = b.SlotId,
                    SlotNumber = b.SlotNumber,
                    TimeSlotId = b.TimeSlotId,
                    OwnerId = b.OwnerId,
                    Status = b.Status,
                    StartTime = b.StartTime,
                    EndTime = b.EndTime,
                    CreatedAt = b.CreatedAt,
                    UpdatedAt = b.UpdatedAt,
                    QrCode = b.QrCode,
                    QrExpiresAt = b.QrExpiresAt,
                    QrImageBase64 = b.QrImageBase64,
                    FormattedStartTime = FormatSriLankaTime(b.StartTime),
                    FormattedEndTime = FormatSriLankaTime(b.EndTime),
                    FormattedDate = FormatSriLankaDate(b.StartTime),
                    CancellationReason = b.CancellationReason

                });
            }

            return bookingDtos;
        }


        public async Task<IEnumerable<BookingDto>> GetBookingsByStationAsync(string stationId)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");
            var list = await bookingCol.Find(b => b.StationId == stationId).SortByDescending(b => b.StartTime).ToListAsync();

            return list.Select(b => new BookingDto
            {
                BookingId = b.BookingId,
                StationId = b.StationId,
                SlotId = b.SlotId,
                SlotNumber = b.SlotNumber,
                TimeSlotId = b.TimeSlotId,
                OwnerId = b.OwnerId,
                Status = b.Status,
                StartTime = b.StartTime,
                EndTime = b.EndTime,
                CreatedAt = b.CreatedAt,
                UpdatedAt = b.UpdatedAt,
                QrCode = b.QrCode,
                QrExpiresAt = b.QrExpiresAt,
                QrImageBase64 = b.QrImageBase64,
                FormattedStartTime = FormatSriLankaTime(b.StartTime),
                FormattedEndTime = FormatSriLankaTime(b.EndTime),
                FormattedDate = FormatSriLankaDate(b.StartTime),
                CancellationReason = b.CancellationReason
            });
        }

        public async Task<IEnumerable<BookingDto>> GetTodayApprovedBookingsAsync(string stationId)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");

            var tz = GetSriLankaTz();
            var nowSL = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);
            var startSL = new DateTime(nowSL.Year, nowSL.Month, nowSL.Day, 0, 0, 0);
            var endSL = startSL.AddDays(1).AddTicks(-1);

            var startUtc = TimeZoneInfo.ConvertTimeToUtc(startSL, tz);
            var endUtc = TimeZoneInfo.ConvertTimeToUtc(endSL, tz);

            var filter = Builders<Booking>.Filter.And(
                Builders<Booking>.Filter.Eq(b => b.StationId, stationId),
                Builders<Booking>.Filter.In(b => b.Status, new[] { "Approved", "Charging" }),
                Builders<Booking>.Filter.Gte(b => b.StartTime, startUtc),
                Builders<Booking>.Filter.Lte(b => b.StartTime, endUtc)
            );

            var list = await bookingCol.Find(filter).SortBy(b => b.StartTime).ToListAsync();

            var bookingDtos = new List<BookingDto>();

            foreach (var b in list)
            {
                var ownerDto = await _evOwnerService.GetEVOwnerById(b.OwnerId);

                bookingDtos.Add(new BookingDto
                {
                    BookingId = b.BookingId,
                    StationId = b.StationId,
                    SlotId = b.SlotId,
                    SlotNumber = b.SlotNumber,
                    TimeSlotId = b.TimeSlotId,
                    OwnerId = b.OwnerId,
                    Status = b.Status,
                    StartTime = b.StartTime,
                    EndTime = b.EndTime,
                    CreatedAt = b.CreatedAt,
                    UpdatedAt = b.UpdatedAt,
                    QrCode = b.QrCode,
                    QrExpiresAt = b.QrExpiresAt,
                    QrImageBase64 = b.QrImageBase64,
                    FormattedStartTime = FormatSriLankaTime(b.StartTime),
                    FormattedEndTime = FormatSriLankaTime(b.EndTime),
                    FormattedDate = FormatSriLankaDate(b.StartTime),
                    CancellationReason = b.CancellationReason,
                    OwnerName = ownerDto?.FullName,
                });
            }
            return bookingDtos;
        }

        public async Task<int> GetApprovedBookingCountByStationAsync(string stationId, bool todayOnly = false)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");
            var tz = GetSriLankaTz();

            var nowSL = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);

            FilterDefinition<Booking> filter;

            if (todayOnly)
            {
                // --- TODAY range in Sri Lanka Time ---
                var startSL = new DateTime(nowSL.Year, nowSL.Month, nowSL.Day, 0, 0, 0);
                var endSL = startSL.AddDays(1).AddTicks(-1);

                var startUtc = TimeZoneInfo.ConvertTimeToUtc(startSL, tz);
                var endUtc = TimeZoneInfo.ConvertTimeToUtc(endSL, tz);

                filter = Builders<Booking>.Filter.And(
                    Builders<Booking>.Filter.Eq(b => b.StationId, stationId),
                    Builders<Booking>.Filter.In(b => b.Status, new[] { "Approved", "Charging" }),
                    Builders<Booking>.Filter.Gte(b => b.StartTime, startUtc),
                    Builders<Booking>.Filter.Lte(b => b.StartTime, endUtc)
                );
            }
            else
            {
                // --- FUTURE range (from tomorrow onwards) ---
                var startSL = nowSL.AddDays(1).Date;
                var startUtc = TimeZoneInfo.ConvertTimeToUtc(startSL, tz);

                filter = Builders<Booking>.Filter.And(
                    Builders<Booking>.Filter.Eq(b => b.StationId, stationId),
                    Builders<Booking>.Filter.Eq(b => b.Status, "Approved"),
                    Builders<Booking>.Filter.Gte(b => b.StartTime, startUtc)
                );
            }

            var count = await bookingCol.CountDocumentsAsync(filter);
            return (int)count;
        }

        public async Task<IEnumerable<BookingDto>> GetUpcomingApprovedBookingsAsync(string stationId)
        {
            var bookingCol = _db.GetCollection<Booking>("Bookings");

            var tz = GetSriLankaTz();
            var nowSL = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);

            var startUtc = TimeZoneInfo.ConvertTimeToUtc(nowSL.AddDays(1).Date, tz);
            var endUtc = TimeZoneInfo.ConvertTimeToUtc(nowSL.AddDays(4).Date.AddTicks(-1), tz);

            var filter = Builders<Booking>.Filter.And(
                Builders<Booking>.Filter.Eq(b => b.StationId, stationId),
                Builders<Booking>.Filter.Eq(b => b.Status, "Approved"),
                Builders<Booking>.Filter.Gte(b => b.StartTime, startUtc),
                Builders<Booking>.Filter.Lte(b => b.StartTime, endUtc)
            );

            var list = await bookingCol.Find(filter).SortBy(b => b.StartTime).ToListAsync();

            var bookingDtos = new List<BookingDto>();

            foreach (var b in list)
            {
                var ownerDto = await _evOwnerService.GetEVOwnerById(b.OwnerId);

                bookingDtos.Add(new BookingDto
                {
                    BookingId = b.BookingId,
                    StationId = b.StationId,
                    SlotId = b.SlotId,
                    SlotNumber = b.SlotNumber,
                    TimeSlotId = b.TimeSlotId,
                    OwnerId = b.OwnerId,
                    Status = b.Status,
                    StartTime = b.StartTime,
                    EndTime = b.EndTime,
                    CreatedAt = b.CreatedAt,
                    UpdatedAt = b.UpdatedAt,
                    QrCode = b.QrCode,
                    QrExpiresAt = b.QrExpiresAt,
                    QrImageBase64 = b.QrImageBase64,
                    FormattedStartTime = FormatSriLankaTime(b.StartTime),
                    FormattedEndTime = FormatSriLankaTime(b.EndTime),
                    FormattedDate = FormatSriLankaDate(b.StartTime),
                    CancellationReason = b.CancellationReason,
                    OwnerName = ownerDto?.FullName,
                });
            }

            return bookingDtos;
        }

        // ------------------------------
        // Operator/Admin actions
        // ------------------------------
        public async Task<bool> ApproveBookingAsync(string bookingId, string operatorId)
        {
            var col = _db.GetCollection<Booking>("Bookings");
            var result = await col.UpdateOneAsync(
                b => b.BookingId == bookingId && b.Status == "Pending",
                Builders<Booking>.Update
                    .Set(b => b.Status, "Approved")
                    .Set(b => b.UpdatedAt, DateTime.UtcNow)
            );
            return result.ModifiedCount > 0;
        }

        public async Task<bool> StartChargingAsync(string bookingId, string operatorId)
        {
            var col = _db.GetCollection<Booking>("Bookings");
            var result = await col.UpdateOneAsync(
                b => b.BookingId == bookingId && (b.Status == "Approved" || b.Status == "Pending"),
                Builders<Booking>.Update
                    .Set(b => b.Status, "Charging")
                    .Set(b => b.UpdatedAt, DateTime.UtcNow)
            );
            return result.ModifiedCount > 0;
        }

        public async Task<bool> FinalizeBookingAsync(string bookingId, string operatorId)
        {
            var col = _db.GetCollection<Booking>("Bookings");
            var result = await col.UpdateOneAsync(
                b => b.BookingId == bookingId && (b.Status == "Charging" || b.Status == "Approved"),
                Builders<Booking>.Update
                    .Set(b => b.Status, "Finalized")
                    .Set(b => b.UpdatedAt, DateTime.UtcNow)
            );
            // We intentionally do NOT flip Slot.Status; bookings depend on Slot.Status == "Available" at create time.
            return result.ModifiedCount > 0;
        }

        // ------------------------------
        // QR
        // ------------------------------
        public async Task<(string? base64Image, DateTime? expiresAt)> GenerateQrCodeAsync(string bookingId)
        {
            var col = _db.GetCollection<Booking>("Bookings");
            var b = await col.Find(x => x.BookingId == bookingId).FirstOrDefaultAsync();
            if (b == null) return (null, null);

            var token = Guid.NewGuid().ToString();
            var expiresAt = b.StartTime > DateTime.UtcNow.AddMinutes(15)
                ? DateTime.UtcNow.AddMinutes(15)
                : b.StartTime;

            using var qrGen = new QRCodeGenerator();
            var qrData = qrGen.CreateQrCode(token, QRCodeGenerator.ECCLevel.Q);
            var pngQr = new PngByteQRCode(qrData);
            var base64 = Convert.ToBase64String(pngQr.GetGraphic(20));

            await col.UpdateOneAsync(
                x => x.BookingId == bookingId,
                Builders<Booking>.Update
                    .Set(x => x.QrCode, token)
                    .Set(x => x.QrExpiresAt, expiresAt)
                    .Set(x => x.QrImageBase64, base64)
                    .Set(x => x.UpdatedAt, DateTime.UtcNow)
            );

            return (base64, expiresAt);
        }

        // ------------------------------
        // Counts
        // ------------------------------
        public async Task<long> CountPendingBookingsAsync()
        {
            var col = _db.GetCollection<Booking>("Bookings");
            return await col.CountDocumentsAsync(b => b.Status == "Pending");
        }

        public async Task<long> CountApprovedFutureBookingsAsync()
        {
            var col = _db.GetCollection<Booking>("Bookings");
            return await col.CountDocumentsAsync(b => b.Status == "Approved" && b.StartTime > DateTime.UtcNow);
        }

        public async Task<int> AutoCancelFutureBookingsForSlotAsync(
            string slotId,
            string reason = "Slot unavailable",
            string cancelledBy = "System")
        {
            var bookings = _db.GetCollection<Booking>("Bookings");
            var timeSlots = _db.GetCollection<TimeSlot>("TimeSlots");
            var now = DateTime.UtcNow;

            var filter = Builders<Booking>.Filter.And(
                Builders<Booking>.Filter.Eq(b => b.SlotId, slotId),
                Builders<Booking>.Filter.In(b => b.Status, new[] { "Pending", "Approved" }),
                Builders<Booking>.Filter.Gt(b => b.StartTime, now)
            );

            var future = await bookings.Find(filter).ToListAsync();
            if (!future.Any()) return 0;

            var ids = future.Select(f => f.BookingId).ToList();
            var tsIds = future.Select(f => f.TimeSlotId).Distinct().ToList();

            await bookings.UpdateManyAsync(
                Builders<Booking>.Filter.In(b => b.BookingId, ids),
                Builders<Booking>.Update
                    .Set(b => b.Status, "Cancelled")
                    .Set(b => b.CancellationReason, reason)
                    .Set(b => b.CancelledBy, cancelledBy)
                    .Set(b => b.UpdatedAt, now)
            );

            await timeSlots.UpdateManyAsync(
                Builders<TimeSlot>.Filter.In(t => t.TimeSlotId, tsIds),
                Builders<TimeSlot>.Update.Set(t => t.Status, "Available")
            );

            return ids.Count;
        }



        public async Task<object> GetReservationOverviewAsync(DateTime? fromDate, DateTime? toDate)
        {
            // Create the filters
            var filter = Builders<Booking>.Filter.Empty;

            if (fromDate.HasValue)
                filter &= Builders<Booking>.Filter.Gte(b => b.StartTime, fromDate.Value);

            if (toDate.HasValue)
                filter &= Builders<Booking>.Filter.Lte(b => b.EndTime, toDate.Value);

            var bookingCol = _db.GetCollection<Booking>("Bookings");

            // Pending Reservation count
            var pendingFilter = filter & Builders<Booking>.Filter.Eq(b => b.Status, "Pending");
            var pendingCount = await bookingCol.CountDocumentsAsync(pendingFilter);

            // Approved Reservation count
            var approvedFilter = filter & Builders<Booking>.Filter.Eq(b => b.Status, "Approved");
            var approvedCount = await bookingCol.CountDocumentsAsync(approvedFilter);

            // Charging Reservation count
            var chargingFilter = filter & Builders<Booking>.Filter.Eq(b => b.Status, "Charging");
            var chargingCount = await bookingCol.CountDocumentsAsync(chargingFilter);

            // Completed Reservation count
            var completedFilter = filter & Builders<Booking>.Filter.In(b => b.Status, new[] { "Completed", "Finalized" });
            var completedCount = await bookingCol.CountDocumentsAsync(completedFilter);

            // Return the counts
            return new
            {
                PendingReservations = pendingCount,
                ApprovedReservations = approvedCount,
                ChargingReservations = chargingCount,
                CompletedReservations = completedCount
            };
        }

        public async Task<PagedResultDto<BookingDto>> GetPendingBookingsAsync(int pageNumber, int pageSize)
        {
            if (pageNumber < 1) pageNumber = 1;
            if (pageSize < 1) pageSize = 10;

            var col = _db.GetCollection<Booking>("Bookings");
            var totalCount = await col.CountDocumentsAsync(b => b.Status == "Pending");

            var list = await col.Find(b => b.Status == "Pending")
                                .SortByDescending(b => b.CreatedAt)
                                .Skip((pageNumber - 1) * pageSize)
                                .Limit(pageSize)
                                .ToListAsync();

            var bookingDtos = list.Select(b => new BookingDto
            {
                BookingId = b.BookingId,
                StationId = b.StationId,
                SlotId = b.SlotId,
                SlotNumber = b.SlotNumber,
                TimeSlotId = b.TimeSlotId,
                OwnerId = b.OwnerId,
                Status = b.Status,
                StartTime = b.StartTime,
                EndTime = b.EndTime,
                CreatedAt = b.CreatedAt,
                UpdatedAt = b.UpdatedAt,
                FormattedStartTime = FormatSriLankaTime(b.StartTime),
                FormattedEndTime = FormatSriLankaTime(b.EndTime),
                FormattedDate = FormatSriLankaDate(b.StartTime),
                StationName = _stationService.GetStationByIdAsync(b.StationId).Result?.Name,
                TimeSlotRange = _db.GetCollection<TimeSlot>("TimeSlots")
                    .Find(t => t.TimeSlotId == b.TimeSlotId)
                    .FirstOrDefault()?.StartTime.ToLocalTime().ToString("hh:mm tt") + " - " +
                    _db.GetCollection<TimeSlot>("TimeSlots")
                    .Find(t => t.TimeSlotId == b.TimeSlotId)
                    .FirstOrDefault()?.EndTime.ToLocalTime().ToString("hh:mm tt")
            });

            return new PagedResultDto<BookingDto>
            {
                Items = bookingDtos,
                TotalCount = totalCount,
                TotalPages = (int)Math.Ceiling(totalCount / (double)pageSize),
                CurrentPage = pageNumber,
                PageSize = pageSize
            };
        }

        public async Task<PagedResultDto<BookingDto>> GetApprovedBookingsAsync(int pageNumber, int pageSize)
        {
            if (pageNumber < 1) pageNumber = 1;
            if (pageSize < 1) pageSize = 10;

            var col = _db.GetCollection<Booking>("Bookings");
            var totalCount = await col.CountDocumentsAsync(b => b.Status == "Approved");

            var list = await col.Find(b => b.Status == "Approved")
                                .SortBy(b => b.CreatedAt)
                                .Skip((pageNumber - 1) * pageSize)
                                .Limit(pageSize)
                                .ToListAsync();

            var bookingDtos = new List<BookingDto>();

            foreach (var b in list)
            {
                var stationDto = await _stationService.GetStationByIdAsync(b.StationId);
                var ownerDto = await _evOwnerService.GetEVOwnerById(b.OwnerId);

                bookingDtos.Add(new BookingDto
                {
                    BookingId = b.BookingId,
                    StationId = b.StationId,
                    SlotId = b.SlotId,
                    SlotNumber = b.SlotNumber,
                    TimeSlotId = b.TimeSlotId,
                    OwnerId = b.OwnerId,
                    Status = b.Status,
                    StartTime = b.StartTime,
                    EndTime = b.EndTime,
                    CreatedAt = b.CreatedAt,
                    UpdatedAt = b.UpdatedAt,
                    FormattedStartTime = FormatSriLankaTime(b.StartTime),
                    FormattedEndTime = FormatSriLankaTime(b.EndTime),
                    FormattedDate = FormatSriLankaDate(b.StartTime),
                    StationName = stationDto?.Name,
                    OwnerName = ownerDto?.FullName
                });
            }

            return new PagedResultDto<BookingDto>
            {
                Items = bookingDtos,
                TotalCount = totalCount,
                TotalPages = (int)Math.Ceiling(totalCount / (double)pageSize),
                CurrentPage = pageNumber,
                PageSize = pageSize
            };
        }
        public async Task<PagedResultDto<BookingDto>> GetCompletedBookingsAsync(int pageNumber, int pageSize)
        {
            if (pageNumber < 1) pageNumber = 1;
            if (pageSize < 1) pageSize = 10;

            var col = _db.GetCollection<Booking>("Bookings");
            var totalCount = await col.CountDocumentsAsync(b => b.Status == "Finalized" || b.Status == "Completed");

            var list = await col.Find(b => b.Status == "Finalized" || b.Status == "Completed")
                                .SortBy(b => b.CreatedAt)
                                .Skip((pageNumber - 1) * pageSize)
                                .Limit(pageSize)
                                .ToListAsync();

            var bookingDtos = new List<BookingDto>();

            foreach (var b in list)
            {
                var stationDto = await _stationService.GetStationByIdAsync(b.StationId);
                var ownerDto = await _evOwnerService.GetEVOwnerById(b.OwnerId);

                bookingDtos.Add(new BookingDto
                {
                    BookingId = b.BookingId,
                    StationId = b.StationId,
                    SlotId = b.SlotId,
                    SlotNumber = b.SlotNumber,
                    TimeSlotId = b.TimeSlotId,
                    OwnerId = b.OwnerId,
                    Status = b.Status,
                    StartTime = b.StartTime,
                    EndTime = b.EndTime,
                    CreatedAt = b.CreatedAt,
                    UpdatedAt = b.UpdatedAt,
                    FormattedStartTime = FormatSriLankaTime(b.StartTime),
                    FormattedEndTime = FormatSriLankaTime(b.EndTime),
                    FormattedDate = FormatSriLankaDate(b.StartTime),
                    StationName = stationDto?.Name,
                    OwnerName = ownerDto?.FullName
                });
            }

            return new PagedResultDto<BookingDto>
            {
                Items = bookingDtos,
                TotalCount = totalCount,
                TotalPages = (int)Math.Ceiling(totalCount / (double)pageSize),
                CurrentPage = pageNumber,
                PageSize = pageSize
            };
        }
    }
}