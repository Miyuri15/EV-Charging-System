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

        public AuthService(IMongoDatabase database, IConfiguration config, IOptions<MongoDbSettings> settings)
        {
            _users = database.GetCollection<User>(settings.Value.UsersCollectionName);
            _evOwners = database.GetCollection<EVOwner>(settings.Value.EVOwnersCollectionName);
            _config = config;
        }

        public async Task<LoginResponseDto> AuthenticateUser(LoginDto loginDto)
        {
            // Try to find user in Users collection
            var user = await _users.Find(u => u.Email == loginDto.Email).FirstOrDefaultAsync();
            if (user != null && user.IsActive)
            {
                if (!BCrypt.Net.BCrypt.Verify(loginDto.Password, user.PasswordHash))
                    throw new AuthenticationException("Invalid credentials");

                var tokenHandler = new JwtSecurityTokenHandler();
                var secretKey = _config["Jwt:Key"] ?? _config["Jwt__Key"];
                var issuer = _config["Jwt:Issuer"] ?? _config["Jwt__Issuer"];
                var audience = _config["Jwt:Audience"] ?? _config["Jwt__Audience"];

                if (string.IsNullOrEmpty(secretKey))
                    throw new InvalidOperationException("JWT Key not found in configuration");

                var key = Encoding.ASCII.GetBytes(secretKey);
                var tokenDescriptor = new SecurityTokenDescriptor
                {
                    Subject = new ClaimsIdentity(new[]
                    {
                        new Claim(ClaimTypes.NameIdentifier, user.Id),
                        new Claim(ClaimTypes.Email, user.Email),
                        new Claim(ClaimTypes.Role, user.Role),
                        new Claim("FullName", user.FullName),
                        new Claim("UserType", "User")
                    }),
                    Expires = DateTime.UtcNow.AddHours(2),
                    Issuer = issuer,
                    Audience = audience,
                    SigningCredentials = new SigningCredentials(
                        new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
                };

                var token = tokenHandler.CreateToken(tokenDescriptor);
                var tokenString = tokenHandler.WriteToken(token);

                return new LoginResponseDto
                {
                    Token = tokenString
                };
            }

            // Try to find user in EVOwners collection
            var evOwner = await _evOwners.Find(o => o.Email == loginDto.Email).FirstOrDefaultAsync();
            if (evOwner != null && evOwner.IsActive)
            {
                if (!BCrypt.Net.BCrypt.Verify(loginDto.Password, evOwner.PasswordHash))
                    throw new AuthenticationException("Invalid credentials");

                var tokenHandler = new JwtSecurityTokenHandler();
                var secretKey = _config["Jwt:Key"] ?? _config["Jwt__Key"];
                var issuer = _config["Jwt:Issuer"] ?? _config["Jwt__Issuer"];
                var audience = _config["Jwt:Audience"] ?? _config["Jwt__Audience"];

                if (string.IsNullOrEmpty(secretKey))
                    throw new InvalidOperationException("JWT Key not found in configuration");

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
                    Expires = DateTime.UtcNow.AddHours(2),
                    Issuer = issuer,
                    Audience = audience,
                    SigningCredentials = new SigningCredentials(
                        new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
                };

                var token = tokenHandler.CreateToken(tokenDescriptor);
                var tokenString = tokenHandler.WriteToken(token);

                return new LoginResponseDto
                {
                    Token = tokenString
                };
            }

            throw new AuthenticationException("Invalid credentials or user inactive");
        }

    }
}