using System;
using EvBackend.Models.DTOs;

namespace EvBackend.Services.Interfaces;

public interface IStationService
{
        Task<StationDto> CreateStationAsync(CreateStationDto dto);
        Task<StationDto> UpdateStationAsync(string stationId, UpdateStationDto dto);
        Task<bool> DeactivateStationAsync(string stationId);
        Task<StationDto> GetStationByIdAsync(string stationId);
        Task<IEnumerable<StationDto>> GetAllStationsAsync(bool onlyActive = false);
        Task<IEnumerable<StationDto>> SearchStationsAsync(string type, string location);
        Task<bool> HasActiveBookingsAsync(string stationId); // extra logic
    
}
