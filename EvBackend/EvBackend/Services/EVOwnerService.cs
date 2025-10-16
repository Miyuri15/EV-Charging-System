// --------------------------------------------------------------
// File Name: EVOwnerService.cs
// Author: Oshadi Jayananda
// Description: Implements business logic for electric vehicle owner management
// Created On: 13/09/2025
// --------------------------------------------------------------

using EvBackend.Entities;
using EvBackend.Models.DTOs;
using EvBackend.Services.Interfaces;
using EvBackend.Settings;
using Microsoft.Extensions.Options;
using MongoDB.Driver;

namespace EvBackend.Services
{
    public class EVOwnerService : IEVOwnerService
    {
        // MongoDB collection for EV owners
        private readonly IMongoCollection<EVOwner> _owners;

        // Notification service for sending alerts
        private readonly INotificationService _notificationService;

        // Constructor to initialize MongoDB collection and notification service
        public EVOwnerService(IMongoDatabase database, IOptions<MongoDbSettings> settings, INotificationService notificationService)
        {
            _owners = database.GetCollection<EVOwner>(settings.Value.EVOwnersCollectionName);
            _notificationService = notificationService;
        }

        // Activate or Deactivate by backoffice (admin)
        public async Task<bool> ChangeEVOwnerStatus(string nic, bool isActive)
        {
            var updateBuilder = Builders<EVOwner>.Update;
            UpdateDefinition<EVOwner> update;

            if (isActive)
            {
                // When activating, clear the reactivation request and set active
                update = updateBuilder
                    .Set(o => o.IsActive, true)
                    .Set(o => o.ReactivationRequested, false);
            }
            else
            {
                // When deactivating, just set active to false (keep reactivation request as is)
                update = updateBuilder.Set(o => o.IsActive, false);
            }

            var result = await _owners.UpdateOneAsync(o => o.NIC == nic, update);

            var owner = await _owners.Find(o => o.NIC == nic).FirstOrDefaultAsync();
            string evOwnerName = owner?.FullName ?? "Unknown";

            if (isActive)
            {
                await _notificationService.SendNotification(nic, "Your account has been activated.");
            }
            else
            {
                await _notificationService.SendNotification(nic, "Your account has been deactivated.");
                await _notificationService.SendNotificationToAdmins($"EV owner {evOwnerName} account deactivated.");
            }
            return result.ModifiedCount > 0;
        }

        // Create new owner
        public async Task<EVOwnerDto> CreateEVOwner(CreateEVOwnerDto dto)
        {
            dto.Email = dto.Email.Trim().ToLower();
            if (await _owners.Find(u => u.Email == dto.Email).AnyAsync())
                throw new ArgumentException("Email already in use");

            if (await _owners.Find(u => u.NIC == dto.NIC).AnyAsync())
                throw new ArgumentException("NIC already in use");

            var owner = new EVOwner
            {
                NIC = dto.NIC,
                FullName = dto.FullName,
                Email = dto.Email,
                Phone = dto.Phone,
                PasswordHash = BCrypt.Net.BCrypt.HashPassword(dto.Password),
                CreatedAt = DateTime.UtcNow
            };

            await _owners.InsertOneAsync(owner);

            return new EVOwnerDto
            {
                FullName = owner.FullName,
                Email = owner.Email,
                Phone = owner.Phone,
                IsActive = owner.IsActive,
                CreatedAt = owner.CreatedAt,
                NIC = owner.NIC
            };
        }

        // Get all owners with pagination
        public Task<IEnumerable<EVOwnerDto>> GetAllEVOwners(int page, int pageSize)
        {
            var owners = _owners.Find(_ => true)
                .Skip((page - 1) * pageSize)
                .Limit(pageSize)
                .ToList();
            var dtos = owners.Select(owner => new EVOwnerDto
            {
                FullName = owner.FullName,
                Email = owner.Email,
                Phone = owner.Phone,
                IsActive = owner.IsActive,
                CreatedAt = owner.CreatedAt,
                NIC = owner.NIC
            });
            return Task.FromResult(dtos);
        }

        // Get owner by NIC
        public Task<EVOwnerDto> GetEVOwnerByNIC(string nic)
        {
            var owner = _owners.Find(o => o.NIC == nic).FirstOrDefault();
            if (owner == null) throw new KeyNotFoundException("EV Owner not found");
            var dto = new EVOwnerDto
            {
                FullName = owner.FullName,
                Email = owner.Email,
                Phone = owner.Phone,
                IsActive = owner.IsActive,
                CreatedAt = owner.CreatedAt,
                NIC = owner.NIC
            };
            return Task.FromResult(dto);
        }

        // Update owner details
        // public async Task<EVOwnerDto> UpdateEVOwner(string nic, UpdateEVOwnerDto dto)
        // {
        //     dto.Email = dto.Email.Trim().ToLower();
        //     var update = Builders<EVOwner>.Update
        //         .Set(o => o.FullName, dto.FullName)
        //         .Set(o => o.Email, dto.Email)
        //         .Set(o => o.Phone, dto.Phone);
        //     var result = await _owners.FindOneAndUpdateAsync(
        //         o => o.NIC == nic,
        //         update,
        //         new FindOneAndUpdateOptions<EVOwner> { ReturnDocument = ReturnDocument.After });

        //     if (result == null)
        //         throw new KeyNotFoundException("EV Owner not found");

        //     return new EVOwnerDto
        //     {
        //         NIC = result.NIC,
        //         FullName = result.FullName,
        //         Email = result.Email,
        //         Phone = result.Phone,
        //         IsActive = result.IsActive,
        //         CreatedAt = result.CreatedAt
        //     };
        // }
        public async Task<EVOwnerDto> UpdateEVOwner(string nic, UpdateEVOwnerDto dto)
        {
            // Fetch the current owner
            var owner = await _owners.Find(o => o.NIC == nic).FirstOrDefaultAsync();
            if (owner == null)
                throw new KeyNotFoundException("EV Owner not found.");

            // Prepare update builder dynamically (only update non-empty fields)
            var updateBuilder = Builders<EVOwner>.Update;
            var updates = new List<UpdateDefinition<EVOwner>>();

            if (!string.IsNullOrWhiteSpace(dto.FullName))
                updates.Add(updateBuilder.Set(o => o.FullName, dto.FullName));

            if (!string.IsNullOrWhiteSpace(dto.Email))
                updates.Add(updateBuilder.Set(o => o.Email, dto.Email.Trim().ToLower()));

            if (!string.IsNullOrWhiteSpace(dto.Phone))
                updates.Add(updateBuilder.Set(o => o.Phone, dto.Phone));

            // Only apply if at least one field was changed
            if (updates.Count == 0)
                return new EVOwnerDto
                {
                    NIC = owner.NIC,
                    FullName = owner.FullName,
                    Email = owner.Email,
                    Phone = owner.Phone,
                    IsActive = owner.IsActive,
                    ReactivationRequested = owner.ReactivationRequested,
                    CreatedAt = owner.CreatedAt
                };

            var combinedUpdate = updateBuilder.Combine(updates);

            // Execute Mongo update and return updated object
            var updatedOwner = await _owners.FindOneAndUpdateAsync(
                o => o.NIC == nic,
                combinedUpdate,
                new FindOneAndUpdateOptions<EVOwner> { ReturnDocument = ReturnDocument.After });

            if (updatedOwner == null)
                throw new KeyNotFoundException("EV Owner not found after update.");

            return new EVOwnerDto
            {
                NIC = updatedOwner.NIC,
                FullName = updatedOwner.FullName,
                Email = updatedOwner.Email,
                Phone = updatedOwner.Phone,
                IsActive = updatedOwner.IsActive,
                ReactivationRequested = updatedOwner.ReactivationRequested,
                CreatedAt = updatedOwner.CreatedAt
            };
        }



        // Request reactivation by owner
        public async Task<bool> RequestReactivation(string nic)
        {
            var owner = await _owners.Find(o => o.NIC == nic).FirstOrDefaultAsync();
            if (owner == null) throw new KeyNotFoundException("EV Owner not found");

            // Check if account is already active
            if (owner.IsActive)
                throw new InvalidOperationException("Account is already active.");

            // Check if reactivation is already requested
            if (owner.ReactivationRequested)
                throw new InvalidOperationException("Reactivation already requested. Please wait for admin approval.");

            // Set reactivation requested to true
            var update = Builders<EVOwner>.Update.Set(o => o.ReactivationRequested, true);
            var result = await _owners.UpdateOneAsync(o => o.NIC == nic, update);

            // Notify admins about the reactivation request
            await _notificationService.SendNotificationToAdmins($"EV owner {owner.FullName} has requested account reactivation.");

            return result.ModifiedCount > 0;
        }

        // Get count of reactivation requests, only for admin
        public async Task<int> GetReactivationRequestCount()
        {
            var count = await _owners.CountDocumentsAsync(o => o.ReactivationRequested == true);
            return (int)count;
        }

        // Get list of reactivation requests, only for admin
        public async Task<IEnumerable<EVOwnerDto>> GetEVOwnersWithReactivationRequests()
        {
            var owners = await _owners.Find(o => o.ReactivationRequested == true && o.IsActive == false)
                .ToListAsync();

            var dtos = owners.Select(owner => new EVOwnerDto
            {
                FullName = owner.FullName,
                Email = owner.Email,
                Phone = owner.Phone,
                IsActive = owner.IsActive,
                ReactivationRequested = owner.ReactivationRequested,
                CreatedAt = owner.CreatedAt,
                NIC = owner.NIC
            });

            return dtos;
        }

        // Clear reactivation request by admin
        public async Task<bool> ClearReactivationRequest(string nic)
        {
            var update = Builders<EVOwner>.Update.Set(o => o.ReactivationRequested, false);
            var result = await _owners.UpdateOneAsync(o => o.NIC == nic, update);
            return result.ModifiedCount > 0;
        }

        public async Task<EVOwnerDto> GetEVOwnerById(string nic)
        {
            var owner = await _owners.Find(o => o.NIC == nic).FirstOrDefaultAsync();

            if (owner == null)
                throw new KeyNotFoundException("EV Owner not found");

            return new EVOwnerDto
            {
                NIC = owner.NIC,
                FullName = owner.FullName,
                Email = owner.Email,
                Phone = owner.Phone,
                IsActive = owner.IsActive,
                ReactivationRequested = owner.ReactivationRequested,
                CreatedAt = owner.CreatedAt
            };
        }

    }
}
