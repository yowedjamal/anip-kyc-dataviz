<?php

namespace App\DTOs\Analytics;

use Carbon\Carbon;

class TimeSeriesData
{
    private array $points = [];
    private array $trend = [];
    private array $seasonality = [];

    public function addDataPoint(Carbon $timestamp, int $total, int $completed, int $failed, ?float $avg = null, $median = null, $p95 = null): void
    {
        $this->points[] = [
            'timestamp' => $timestamp->toIso8601String(),
            'total_sessions' => $total,
            'completed_sessions' => $completed,
            'failed_sessions' => $failed,
            'avg_processing_time' => $avg,
            'median_processing_time' => $median,
            'p95_processing_time' => $p95,
        ];
    }

    public function setTrend(array $trend): void { $this->trend = $trend; }
    public function setSeasonality(array $s): void { $this->seasonality = $s; }

    public function toArray(): array
    {
        return [
            'points' => $this->points,
            'trend' => $this->trend,
            'seasonality' => $this->seasonality,
        ];
    }
}
