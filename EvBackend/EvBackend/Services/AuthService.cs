// --------------------------------------------------------------
// File Name: AuthService.cs
// Author: Hasindu Koshitha
// Description: Implements business logic for user authentication
// Created On: 25/09/2025
// --------------------------------------------------------------
using System.IdentityModel.Tokens.Jwt;
using System.Security.Authentication;
using System.Security.Claims;
using System.Text;
using EvBackend.Entities;
using EvBackend.Models.DTOs;
using EvBackend.Services.Interfaces;
using EvBackend.Settings;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using MongoDB.Driver;

namespace EvBackend.Services
{
    public class AuthService : IAuthService
    {
        private readonly IMongoCollection<User> _users;
        private readonly IMongoCollection<EVOwner> _evOwners;
        private readonly IConfiguration _config;
        private readonly IEmailService _emailService;

        public AuthService(IMongoDatabase database, IConfiguration config, IOptions<MongoDbSettings> settings, IEmailService emailService)
        {
            _users = database.GetCollection<User>(settings.Value.UsersCollectionName);
            _evOwners = database.GetCollection<EVOwner>(settings.Value.EVOwnersCollectionName);
            _config = config;
            _emailService = emailService;
        }

        public async Task<LoginResponseDto> AuthenticateUser(LoginDto loginDto, HttpRequest request)
        {
            var clientType = request.Headers["X-Client-Type"].ToString()?.Trim();
            bool isWeb = clientType.Equals("Web", StringComparison.OrdinalIgnoreCase);
            bool isMobile = clientType.Equals("Mobile", StringComparison.OrdinalIgnoreCase);

            string[] webRoles = { "Admin", "Operator" };
            string[] mobileRoles = { "Operator", "Owner" };

            loginDto.Email = loginDto.Email.Trim().ToLower();

            // --- Check User Collection ---
            var user = await _users.Find(u => u.Email == loginDto.Email).FirstOrDefaultAsync();
            if (user != null && user.IsActive)
            {
                if (!BCrypt.Net.BCrypt.Verify(loginDto.Password, user.PasswordHash))
                    throw new AuthenticationException("Invalid credentials");

                bool allowed =
                    (isWeb && webRoles.Contains(user.Role)) ||
                    (isMobile && mobileRoles.Contains(user.Role));

                if (!allowed)
                    throw new AuthenticationException("Access denied from this platform");

                var tokenHandler = new JwtSecurityTokenHandler();
                var secretKey = _config["Jwt:Key"] ?? _config["Jwt__Key"];
                var issuer = _config["Jwt:Issuer"] ?? _config["Jwt__Issuer"];
                var audience = _config["Jwt:Audience"] ?? _config["Jwt__Audience"];

                if (string.IsNullOrEmpty(secretKey))
                    throw new InvalidOperationException("JWT Key not found in configuration");

                var key = Encoding.ASCII.GetBytes(secretKey);

                var claims = new List<Claim>
                {
                    new Claim(ClaimTypes.NameIdentifier, user.Id),
                    new Claim(ClaimTypes.Email, user.Email),
                    new Claim(ClaimTypes.Role, user.Role),
                    new Claim("FullName", user.FullName),
                    new Claim("UserType", "User"),
                    new Claim("userId", user.Id)
                };

                // Include station info for Operator
                if (user.Role.Equals("Operator", StringComparison.OrdinalIgnoreCase))
                {
                    claims.Add(new Claim("stationId", user.StationId ?? ""));
                    claims.Add(new Claim("stationName", user.StationName ?? ""));
                    claims.Add(new Claim("stationLocation", user.StationLocation ?? ""));
                }

                var tokenDescriptor = new SecurityTokenDescriptor
                {
                    Subject = new ClaimsIdentity(claims),
                    Expires = DateTime.UtcNow.AddHours(12),
                    Issuer = issuer,
                    Audience = audience,
                    SigningCredentials = new SigningCredentials(
                        new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
                };

                var token = tokenHandler.CreateToken(tokenDescriptor);
                var tokenString = tokenHandler.WriteToken(token);

                return new LoginResponseDto { Token = tokenString };
            }

            // --- Check EV Owner Collection ---
            var evOwner = await _evOwners.Find(o => o.Email == loginDto.Email).FirstOrDefaultAsync();
            if (evOwner != null)
            {
                if (!BCrypt.Net.BCrypt.Verify(loginDto.Password, evOwner.PasswordHash))
                    throw new AuthenticationException("Invalid credentials");

                bool allowed =
                    (isWeb && webRoles.Contains("Owner")) ||
                    (isMobile && mobileRoles.Contains("Owner"));

                if (!allowed)
                    throw new AuthenticationException("Access denied from this platform");

                var tokenHandler = new JwtSecurityTokenHandler();
                var secretKey = _config["Jwt:Key"] ?? _config["Jwt__Key"];
                var issuer = _config["Jwt:Issuer"] ?? _config["Jwt__Issuer"];
                var audience = _config["Jwt:Audience"] ?? _config["Jwt__Audience"];

                var key = Encoding.ASCII.GetBytes(secretKey);

                var tokenDescriptor = new SecurityTokenDescriptor
                {
                    Subject = new ClaimsIdentity(new[]
                    {
                new Claim(ClaimTypes.NameIdentifier, evOwner.NIC),
                new Claim(ClaimTypes.Email, evOwner.Email),
                new Claim(ClaimTypes.Role, "Owner"),
                new Claim("FullName", evOwner.FullName),
                new Claim("UserType", "EVOwner")
            }),
                    Expires = DateTime.UtcNow.AddHours(12),
                    Issuer = issuer,
                    Audience = audience,
                    SigningCredentials = new SigningCredentials(
                        new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
                };

                var token = tokenHandler.CreateToken(tokenDescriptor);
                var tokenString = tokenHandler.WriteToken(token);

                return new LoginResponseDto { Token = tokenString };
            }

            throw new AuthenticationException("Invalid credentials or user inactive");
        }

        public async Task SendPasswordResetEmail(ForgotPasswordDto forgotPasswordDto)
        {
            // Find user by email in Users collection
            var user = await _users.Find(u => u.Email == forgotPasswordDto.email).FirstOrDefaultAsync();
            var owner = await _evOwners.Find(o => o.Email == forgotPasswordDto.email).FirstOrDefaultAsync();

            if (user != null)
            {
                // Generate a password reset token
                var resetToken = Guid.NewGuid().ToString();
                // Save the reset token to the database
                user.PasswordResetToken = resetToken;
                user.PasswordResetTokenExpiration = DateTime.UtcNow.AddHours(1);
                await _users.ReplaceOneAsync(u => u.Id == user.Id, user);

                // Send the password reset email (you'll need to implement this)
                await _emailService.SendPasswordResetEmail(user.Email, resetToken);
            }
            else if (owner != null)
            {
                // Generate a password reset token
                var resetToken = Guid.NewGuid().ToString();
                // Save the reset token to the database
                owner.PasswordResetToken = resetToken;
                owner.PasswordResetTokenExpiration = DateTime.UtcNow.AddHours(1);
                await _evOwners.ReplaceOneAsync(o => o.NIC == owner.NIC, owner);

                // Send the password reset email (you'll need to implement this)
                await _emailService.SendPasswordResetEmail(owner.Email, resetToken);
            }
        }

        public async Task ResetPassword(ResetPasswordDto resetPasswordDto)
        {
            // Try to find user in Users collection by reset token
            var user = await _users.Find(u => u.PasswordResetToken == resetPasswordDto.resetToken).FirstOrDefaultAsync();
            if (user != null)
            {
                if (user.PasswordResetTokenExpiration < DateTime.UtcNow)
                    throw new InvalidOperationException("Reset token has expired");

                // Update the password
                user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(resetPasswordDto.newPassword);
                user.PasswordResetToken = null;
                user.PasswordResetTokenExpiration = null;
                await _users.ReplaceOneAsync(u => u.Id == user.Id, user);
                return;
            }

            // Try to find user in EVOwners collection by reset token
            var owner = await _evOwners.Find(o => o.PasswordResetToken == resetPasswordDto.resetToken).FirstOrDefaultAsync();
            if (owner != null)
            {
                if (owner.PasswordResetTokenExpiration < DateTime.UtcNow)
                    throw new InvalidOperationException("Reset token has expired");

                // Update the password
                owner.PasswordHash = BCrypt.Net.BCrypt.HashPassword(resetPasswordDto.newPassword);
                owner.PasswordResetToken = null;
                owner.PasswordResetTokenExpiration = null;
                await _evOwners.ReplaceOneAsync(o => o.NIC == owner.NIC, owner);
                return;
            }

            throw new KeyNotFoundException("Invalid reset token");
        }

        public async Task ChangePassword(string userId, ChangePasswordDto changePasswordDto)
        {
            var user = await _users.Find(u => u.Id == userId).FirstOrDefaultAsync();
            if (user == null)
                throw new KeyNotFoundException("User not found");

            // Verify old password
            if (!BCrypt.Net.BCrypt.Verify(changePasswordDto.OldPassword, user.PasswordHash))
                throw new AuthenticationException("Current password is incorrect");

            // Update to new password
            user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(changePasswordDto.NewPassword);
            await _users.ReplaceOneAsync(u => u.Id == user.Id, user);
        }
    }
}