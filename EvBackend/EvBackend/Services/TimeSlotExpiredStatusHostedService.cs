using System;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace EvBackend.Services
{
    public class TimeSlotExpiredStatusHostedService : BackgroundService
    {
        private readonly TimeSlotSchedulerService _scheduler;
        private readonly ILogger<TimeSlotExpiredStatusHostedService> _logger;
        private readonly TimeSpan _interval = TimeSpan.FromHours(2);

        public TimeSlotExpiredStatusHostedService(TimeSlotSchedulerService scheduler, ILogger<TimeSlotExpiredStatusHostedService> logger)
        {
            _scheduler = scheduler;
            _logger = logger;
        }

        protected override async Task ExecuteAsync(CancellationToken stoppingToken)
        {
            _logger.LogInformation("TimeSlotExpiredStatusHostedService started. Running every {Hours} hours.", _interval.TotalHours);

            // Run immediately on start, then every _interval
            while (!stoppingToken.IsCancellationRequested)
            {
                try
                {
                    await _scheduler.UpdateExpiredTimeSlotsToAvailableAsync();
                }
                catch (Exception ex)
                {
                    _logger.LogError(ex, "Error while updating expired timeslots.");
                }

                await Task.Delay(_interval, stoppingToken);
            }

            _logger.LogInformation("TimeSlotExpiredStatusHostedService stopping.");
        }
    }
}