// --------------------------------------------------------------
// File Name: IBookingService.cs
// Author: Miyuri Lokuhewage
// Description: Booking management contract for new flow.
// Created/Updated On: 09/10/2025
// --------------------------------------------------------------

using EvBackend.Models.DTOs;
using Microsoft.AspNetCore.Mvc;

namespace EvBackend.Services.Interfaces
{
    public interface IBookingService
    {
        // Owner actions
        Task<BookingDto> CreateBookingAsync(CreateBookingDto dto, string ownerId);
        Task<BookingDto> UpdateBookingAsync(string bookingId, UpdateBookingDto dto, string requesterId, string requesterRole);
        Task<bool> CancelBookingAsync(string bookingId, string requesterId, string requesterRole);

        // Fetching
        Task<BookingDto> GetBookingByIdAsync(string bookingId);
        Task<IEnumerable<BookingDto>> GetBookingsByOwnerAsync(string ownerId);
        Task<IEnumerable<BookingDto>> GetBookingsByStationAsync(string stationId);
        Task<IEnumerable<BookingDto>> GetTodayApprovedBookingsAsync(string stationId);
        Task<int> GetApprovedBookingCountByStationAsync(string stationId, bool todayOnly = false);
        Task<IEnumerable<BookingDto>> GetUpcomingApprovedBookingsAsync(string stationId);

        // Operator/Admin
        Task<bool> ApproveBookingAsync(string bookingId, string operatorId);
        Task<bool> StartChargingAsync(string bookingId, string operatorId);
        Task<bool> FinalizeBookingAsync(string bookingId, string operatorId);

        // Availability for UI
        Task<IEnumerable<object>> GetAvailableTimeSlotsForStationAsync(string stationId, string dateYyyyMmDd);
        Task<IEnumerable<object>> GetAvailableSlotsForTimeSlotAsync(string timeSlotId);

        // QR
        Task<(string? base64Image, DateTime? expiresAt)> GenerateQrCodeAsync(string bookingId);

        Task<long> CountPendingBookingsAsync();
        Task<long> CountApprovedFutureBookingsAsync();

        Task<int> AutoCancelFutureBookingsForSlotAsync(
            string slotId,
            string reason = "Slot unavailable",
            string cancelledBy = "System");
        Task<object> GetReservationOverviewAsync(DateTime? fromDate, DateTime? toDate);

        Task<PagedResultDto<BookingDto>> GetPendingBookingsAsync(int pageNumber, int pageSize);
        Task<PagedResultDto<BookingDto>> GetApprovedBookingsAsync(int pageNumber, int pageSize);
        Task<PagedResultDto<BookingDto>> GetCompletedBookingsAsync(int pageNumber, int pageSize);
        Task<bool> HasActiveBookingAsync(string ownerId);
    }
}
