// --------------------------------------------------------------
// File Name: UpdateEVOwnerDto.cs
// Author: Oshadi Jayananda
// Description: DTO for partial EV Owner updates (phone optional)
// --------------------------------------------------------------

using System.ComponentModel.DataAnnotations;
using System.Text.Json.Serialization;

namespace EvBackend.Models.DTOs
{
    public class UpdateEVOwnerDto
    {
        [StringLength(100)]
        public string? FullName { get; set; }

        [EmailAddress]
        public string? Email { get; set; }

        // ✅ Optional phone — skips validation if empty or null
        [Phone]
        [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
        public string? Phone 
        { 
            get => _phone;
            set => _phone = string.IsNullOrWhiteSpace(value) ? null : value;
        }
        private string? _phone;
    }
}
